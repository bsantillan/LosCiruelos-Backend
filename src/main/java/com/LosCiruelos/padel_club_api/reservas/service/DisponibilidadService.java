package com.LosCiruelos.padel_club_api.reservas.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.reservas.dto.response.DisponibilidadResponse;
import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.entity.DiaApertura;
import com.LosCiruelos.padel_club_api.Exceptions.DiaSinAperturaException;
import com.LosCiruelos.padel_club_api.Exceptions.HorarioOcupadoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.club.service.CanchaService;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Turno;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijo;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnoFijoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Reglas de horario de las reservas: si un turno es válido (fecha, apertura, duración, bloques de
 * 30 minutos) y si está libre (otras reservas y turnos fijos). La disponibilidad que ve el front
 * usa exactamente las mismas reglas, así solo se ofrecen horarios que después se pueden reservar.
 */
@Service
@RequiredArgsConstructor
public class DisponibilidadService {

    // Reservas que ya no ocupan la cancha
    public static final Set<EstadoReserva> ESTADOS_INACTIVOS = Set.of(EstadoReserva.CANCELADA, EstadoReserva.EXPIRADA);
    // Pagos cobrados o que MP todavía está procesando: una PENDIENTE vencida con uno de estos sigue ocupando
    public static final Set<EstadoPago> PAGOS_ACTIVOS = Set.of(EstadoPago.APROBADO, EstadoPago.EN_PROCESO);

    private final ConfiguracionService configuracionService;
    private final CanchaService canchaService;
    private final ReservaRepository reservaRepository;
    private final TurnoFijoRepository turnoFijoRepository;
    private final Reloj reloj;

    /**
     * Horario del día, turnos ocupados y turnos libres de la duración pedida.
     *
     * @param duracion minutos; si es null se usa la duración mínima configurada
     */
    @Transactional(readOnly = true)
    public DisponibilidadResponse disponibilidad(Long canchaId, LocalDate fecha, Integer duracion) {
        Cancha cancha = canchaService.findByIdOrThrow(canchaId);
        Configuracion config = configuracionService.obtener();
        LocalDateTime ahora = reloj.ahora();

        if (fecha.isBefore(ahora.toLocalDate())) {
            throw new ReservaException("La fecha debe ser hoy o en el futuro.");
        }
        int minutosTurno = duracion != null ? duracion : config.getDuracionMinimaTurno();
        configuracionService.validarDuracion(config, minutosTurno);

        DiaApertura dia = configuracionService.obtenerDiaApertura(config, fecha);
        int apertura = enMinutos(dia.getHorario_inicio());
        int cierre = enMinutos(dia.getHorario_fin());

        // Si es hoy, el primer turno es el próximo bloque de 30 min contado desde la apertura
        int desde = apertura;
        if (fecha.isEqual(ahora.toLocalDate())) {
            int minutoActual = enMinutos(ahora.toLocalTime());
            if (minutoActual > apertura) {
                int bloque = ConfiguracionService.MINUTOS_POR_BLOQUE;
                desde = apertura + (int) Math.ceil((minutoActual - apertura) / (double) bloque) * bloque;
            }
        }

        // todas las reservas caen dentro del horario del día
        List<Reserva> ocupadas = buscarQueOcupan(cancha.getId(), fecha, dia.getHorario_inicio(), dia.getHorario_fin(),
                ahora);
        List<DisponibilidadResponse.Turno> slotsOcupados = new ArrayList<>(ocupadas.stream()
                .map(r -> new DisponibilidadResponse.Turno(r.getHoraInicio(), r.getHoraFin()))
                .toList());
        // los turnos fijos ocupan aunque el partido de esa fecha todavía no se haya creado
        turnosFijosQueBloquean(cancha.getId(), fecha, dia.getHorario_inicio(), dia.getHorario_fin())
                .forEach(tf -> slotsOcupados.add(new DisponibilidadResponse.Turno(tf.getHoraInicio(), tf.getHoraFin())));
        slotsOcupados.sort(Comparator.comparing(DisponibilidadResponse.Turno::getHoraInicio));

        // nunca se pasa del cierre, así que nunca se intenta construir las 24:00
        List<DisponibilidadResponse.Turno> disponibles = new ArrayList<>();
        for (int inicio = desde; inicio + minutosTurno <= cierre; inicio += ConfiguracionService.MINUTOS_POR_BLOQUE) {
            LocalTime horaInicio = desdeMinutos(inicio);
            LocalTime horaFin = desdeMinutos(inicio + minutosTurno);
            boolean libre = slotsOcupados.stream()
                    .noneMatch(t -> Turno.seSuperponen(t.getHoraInicio(), t.getHoraFin(), horaInicio, horaFin));
            if (libre) {
                disponibles.add(new DisponibilidadResponse.Turno(horaInicio, horaFin));
            }
        }

        return new DisponibilidadResponse(
                dia.getHorario_inicio(),
                dia.getHorario_fin(),
                config.getDuracionMinimaTurno(),
                config.getDuracionMaximaTurno(),
                minutosTurno,
                // mismo cálculo que al crear la reserva
                configuracionService.calcularMontoTurno(config, minutosTurno),
                desde < cierre ? desdeMinutos(desde) : null,
                slotsOcupados,
                disponibles);
    }

    /** Valida fecha y horario contra la configuración. Devuelve la duración en minutos. */
    public long validarHorario(Configuracion config, LocalDate fecha, LocalTime inicio, LocalTime fin,
            LocalDateTime ahora) {
        if (fecha.isBefore(ahora.toLocalDate())) {
            throw new ReservaException("La fecha debe ser hoy o en el futuro.");
        }
        if (fecha.isEqual(ahora.toLocalDate()) && inicio.isBefore(ahora.toLocalTime())) {
            throw new ReservaException("No podés reservar un horario que ya pasó.");
        }
        if (!inicio.isBefore(fin)) {
            throw new ReservaException("La hora de inicio debe ser anterior a la hora de fin.");
        }

        long duracion = Turno.minutosEntre(inicio, fin);
        configuracionService.validarDuracion(config, duracion);

        DiaApertura dia = configuracionService.obtenerDiaApertura(config, fecha);
        if (inicio.isBefore(dia.getHorario_inicio())) {
            throw new DiaSinAperturaException(
                    "El horario de apertura el %s es a las %s.".formatted(dia.getDia(), dia.getHorario_inicio()));
        }
        if (fin.isAfter(dia.getHorario_fin())) {
            throw new DiaSinAperturaException(
                    "El horario de cierre el %s es a las %s.".formatted(dia.getDia(), dia.getHorario_fin()));
        }
        if (Turno.minutosEntre(dia.getHorario_inicio(), inicio)
                % ConfiguracionService.MINUTOS_POR_BLOQUE != 0) {
            throw new ReservaException(
                    "El horario de inicio debe coincidir con un turno válido (cada 30 minutos desde la apertura).");
        }
        return duracion;
    }

    /**
     * El turno está libre: no se superpone con otra reserva que ocupe la cancha (salvo
     * {@code excluirReservaId}) ni con un turno fijo que todavía no tiene su partido de esa fecha.
     */
    public void validarLibre(Long canchaId, LocalDate fecha, LocalTime inicio, LocalTime fin, LocalDateTime ahora,
            Long excluirReservaId) {
        validarLibre(canchaId, fecha, inicio, fin, ahora, excluirReservaId, null);
    }

    /** @param excluirTurnoFijoId el turno fijo cuyo partido se está creando o modificando (no se bloquea a sí mismo) */
    public void validarLibre(Long canchaId, LocalDate fecha, LocalTime inicio, LocalTime fin, LocalDateTime ahora,
            Long excluirReservaId, Long excluirTurnoFijoId) {
        boolean ocupado = buscarQueOcupan(canchaId, fecha, inicio, fin, ahora).stream()
                .anyMatch(otra -> !otra.getId().equals(excluirReservaId));
        if (ocupado) {
            throw new HorarioOcupadoException();
        }
        if (turnosFijosQueBloquean(canchaId, fecha, inicio, fin).stream()
                .anyMatch(tf -> !tf.getId().equals(excluirTurnoFijoId))) {
            throw new HorarioOcupadoException("Ese horario es un turno fijo del club en esa cancha.");
        }
    }

    /**
     * Reservas que ocupan el turno: todas menos CANCELADA, EXPIRADA y PENDIENTE vencidas
     * sin pagos activos.
     */
    public List<Reserva> buscarQueOcupan(Long canchaId, LocalDate fecha, LocalTime inicio, LocalTime fin,
            LocalDateTime ahora) {
        return reservaRepository.findQueOcupan(canchaId, fecha, inicio, fin, ahora,
                ESTADOS_INACTIVOS, EstadoReserva.PENDIENTE, PAGOS_ACTIVOS);
    }

    /** Turnos fijos activos que ocupan el horario ese día y todavía no tienen su partido creado. */
    public List<TurnoFijo> turnosFijosQueBloquean(Long canchaId, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        return turnoFijoRepository.findQueBloquean(canchaId, fecha.getDayOfWeek(), fecha, inicio, fin);
    }

    private static int enMinutos(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }

    private static LocalTime desdeMinutos(int minutos) {
        return LocalTime.of(minutos / 60, minutos % 60);
    }
}
