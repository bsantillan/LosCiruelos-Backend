package com.LosCiruelos.padel_club_api.Services;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.DTOs.Responses.CanchaResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.DisponibilidadResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.DisponibilidadResponse.Turno;
import com.LosCiruelos.padel_club_api.Entities.Cancha;
import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.Entities.DiaApertura;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Exceptions.CanchaNotFoundException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.CanchaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CanchaService {

    private final CanchaRepository canchaRepository;
    private final ReservaService reservaService;
    private final ConfiguracionService configuracionService;
    private final Clock clock;

    public Cancha findByIdOrThrow(Long id) {
        return canchaRepository.findById(id)
                .orElseThrow(() -> new CanchaNotFoundException("Cancha no encontrada: " + id));
    }

    public Cancha crearCancha(Integer numero, String descripcion) {
        return canchaRepository.save(Cancha.builder()
                .numero(numero)
                .descripcion(descripcion)
                .build());
    }

    public long count() {
        return canchaRepository.count();
    }

    public List<CanchaResponse> listarCanchas() {
        return canchaRepository.findAll().stream()
                .map(CanchaResponse::from)
                .toList();
    }

    /**
     * Horario del día, turnos ocupados y turnos libres de la duración pedida.
     * Usa exactamente las mismas reglas que la creación de reservas, así el front solo
     * ofrece horarios que después se pueden reservar.
     *
     * @param duracion minutos; si es null se usa la duración mínima configurada
     */
    @Transactional(readOnly = true)
    public DisponibilidadResponse getDisponibilidad(Long canchaId, LocalDate fecha, Integer duracion) {
        Cancha cancha = findByIdOrThrow(canchaId);
        Configuracion config = configuracionService.obtener();
        LocalDateTime ahora = LocalDateTime.now(clock);

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
        List<Reserva> ocupadas = reservaService.buscarQueOcupan(cancha.getId(), fecha,
                dia.getHorario_inicio(), dia.getHorario_fin(), ahora);
        List<Turno> slotsOcupados = ocupadas.stream()
                .map(r -> new Turno(r.getHoraInicio(), r.getHoraFin()))
                .toList();

        // nunca se pasa del cierre, así que nunca se intenta construir las 24:00
        List<Turno> disponibles = new ArrayList<>();
        for (int inicio = desde; inicio + minutosTurno <= cierre; inicio += ConfiguracionService.MINUTOS_POR_BLOQUE) {
            LocalTime horaInicio = desdeMinutos(inicio);
            LocalTime horaFin = desdeMinutos(inicio + minutosTurno);
            boolean libre = ocupadas.stream()
                    .noneMatch(r -> r.getHoraInicio().isBefore(horaFin) && r.getHoraFin().isAfter(horaInicio));
            if (libre) {
                disponibles.add(new Turno(horaInicio, horaFin));
            }
        }

        return new DisponibilidadResponse(
                dia.getHorario_inicio(),
                dia.getHorario_fin(),
                config.getDuracionMinimaTurno(),
                config.getDuracionMaximaTurno(),
                minutosTurno,
                // mismo cálculo que ReservaService al crear la reserva
                configuracionService.calcularMontoTurno(config, minutosTurno),
                desde < cierre ? desdeMinutos(desde) : null,
                slotsOcupados,
                disponibles);
    }

    private static int enMinutos(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }

    private static LocalTime desdeMinutos(int minutos) {
        return LocalTime.of(minutos / 60, minutos % 60);
    }
}
