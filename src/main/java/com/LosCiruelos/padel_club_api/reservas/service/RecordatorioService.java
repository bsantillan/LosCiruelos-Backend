package com.LosCiruelos.padel_club_api.reservas.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaJugador;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.notificaciones.aviso.AvisoReserva;
import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Reloj;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Recordatorio al cliente antes del turno (por defecto 2 h antes), por el canal que eligió.
 * Solo para reservas confirmadas (señadas, parciales o pagas), una sola vez por reserva.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordatorioService {

    private final ReservaRepository reservaRepository;
    private final ReservaService reservaService;
    private final ConfiguracionService configuracionService;
    private final ApplicationEventPublisher eventos;
    private final Reloj reloj;

    /** Reservas cuyo turno empieza dentro de la ventana del recordatorio y todavía no lo recibieron. */
    @Transactional(readOnly = true)
    public List<Long> pendientes() {
        Configuracion config = configuracionService.obtener();
        if (!Boolean.TRUE.equals(config.getRecordatorioActivo())) {
            return List.of();
        }
        LocalDateTime ahora = reloj.ahora();
        LocalDateTime limite = ahora.plusMinutes(config.getMinutosRecordatorio());
        LocalDate hoy = ahora.toLocalDate();

        List<Long> ids = new ArrayList<>();
        if (limite.toLocalDate().equals(hoy)) {
            ids.addAll(reservaRepository.findIdsParaRecordatorio(Reserva.ESTADOS_CONFIRMADOS, hoy,
                    ahora.toLocalTime(), limite.toLocalTime()));
        } else {
            // la ventana pasa la medianoche: lo que queda de hoy y el principio de mañana
            ids.addAll(reservaRepository.findIdsParaRecordatorio(Reserva.ESTADOS_CONFIRMADOS, hoy,
                    ahora.toLocalTime(), LocalTime.MAX));
            ids.addAll(reservaRepository.findIdsParaRecordatorio(Reserva.ESTADOS_CONFIRMADOS,
                    limite.toLocalDate(), LocalTime.MIDNIGHT, limite.toLocalTime()));
        }
        return ids;
    }

    /**
     * Con la reserva bloqueada, vuelve a revisar que corresponda, la marca y publica el aviso
     * (se envía después del commit: si falla no se reintenta, para no mandarlo dos veces).
     *
     * @return true si se mandó
     */
    @Transactional
    public boolean enviar(Long reservaId) {
        Configuracion config = configuracionService.obtener();
        Reserva reserva = reservaService.bloquear(reservaId);
        LocalDateTime ahora = reloj.ahora();
        LocalDateTime inicio = reserva.inicioDelTurno();
        LocalDateTime desdeCuando = inicio.minusMinutes(config.getMinutosRecordatorio());

        boolean corresponde = Reserva.ESTADOS_CONFIRMADOS.contains(reserva.getEstado())
                && (reserva.esPartido() || Boolean.TRUE.equals(reserva.getCliente().getRecibeRecordatorios()))
                && reserva.getRecordatorioEnviadoAt() == null
                && inicio.isAfter(ahora) && !desdeCuando.isAfter(ahora);
        // si la reserva se confirmó dentro de la ventana, el cliente recién reservó: no hace falta
        if (!corresponde || confirmadaEn(reserva).isAfter(desdeCuando)) {
            return false;
        }

        reserva.setRecordatorioEnviadoAt(ahora); // la reserva bloqueada se guarda al terminar la transacción
        if (reserva.esPartido()) {
            avisarJugadores(reserva);
            return true;
        }
        String detalle = reserva.getMontoPendiente().signum() > 0
                ? "Saldo a pagar: " + Dinero.texto(reserva.getMontoPendiente()) + "."
                : "Tu reserva está paga. ¡Que lo disfrutes!";
        eventos.publishEvent(AvisoReserva.de(TipoAviso.RECORDATORIO, reserva, reserva.getMontoPendiente(), detalle));
        log.info("Recordatorio de la reserva #{} ({} {})", reserva.getId(), reserva.getFechaReserva(),
                reserva.getHoraInicio());
        return true;
    }

    /** Partido: a cada jugador registrado que recibe recordatorios, con lo que le falta de su parte. */
    private void avisarJugadores(Reserva reserva) {
        for (ReservaJugador jugador : reserva.getJugadores()) {
            if (jugador.getUsuario() == null
                    || !Boolean.TRUE.equals(jugador.getUsuario().getRecibeRecordatorios())) {
                continue;
            }
            BigDecimal debe = reserva.debeDe(jugador);
            String detalle = debe.signum() > 0
                    ? "Tu parte a pagar en el club: " + Dinero.texto(debe) + "."
                    : "El partido está pago. ¡Que lo disfrutes!";
            eventos.publishEvent(AvisoReserva.para(TipoAviso.RECORDATORIO, reserva, jugador.getUsuario(), debe,
                    detalle));
        }
        log.info("Recordatorio del partido #{} ({} {}) a sus jugadores", reserva.getId(), reserva.getFechaReserva(),
                reserva.getHoraInicio());
    }

    /** Cuándo se confirmó: el primer pago aprobado (o la creación, si no tiene fecha de pago). */
    private static LocalDateTime confirmadaEn(Reserva reserva) {
        return reserva.getPagos().stream()
                .filter(p -> p.getEstado() == EstadoPago.APROBADO)
                .map(Pago::getPaidAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(reserva.getCreatedAt());
    }
}
