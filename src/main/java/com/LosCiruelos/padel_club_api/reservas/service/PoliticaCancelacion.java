package com.LosCiruelos.padel_club_api.reservas.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.Services.ConfiguracionService;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.OpcionReembolso;

import lombok.RequiredArgsConstructor;

/**
 * Reglas de cancelación. Se usan para cancelar y para informarle al front, en cada reserva,
 * si se puede cancelar y cuánto se devolvería (así no replica la regla).
 *
 * Cliente, reserva confirmada (señada, parcial o pagada), hasta que empieza el turno:
 * <ul>
 * <li>faltan al menos {@code minutosReembolsoTotal} (24 h): se devuelve todo lo pagado;</li>
 * <li>faltan al menos {@code minutosReembolsoSenia} (12 h): se devuelve hasta el valor de la seña;</li>
 * <li>faltan menos: se puede cancelar, sin reembolso.</li>
 * </ul>
 * Personal del club: elige TOTAL, SENIA o NADA, hasta que termina el turno.
 * Una reserva PENDIENTE (sin dinero cobrado) se cancela sin reembolso, como antes.
 */
@Component
@RequiredArgsConstructor
public class PoliticaCancelacion {

    private final ConfiguracionService configuracionService;

    /** Lo que se le informa al cliente sobre la cancelación de una reserva. */
    public record Evaluacion(
            boolean cancelable,
            LocalDateTime cancelableHasta,
            BigDecimal montoReembolso,
            LocalDateTime reembolsoTotalHasta,
            LocalDateTime reembolsoSeniaHasta) {

        static Evaluacion noCancelable() {
            return new Evaluacion(false, null, BigDecimal.ZERO, null, null);
        }
    }

    public Evaluacion evaluarParaCliente(Reserva reserva, LocalDateTime ahora) {
        LocalDateTime inicio = reserva.inicioDelTurno();

        // un partido lo arma y lo cancela el ADMIN, no los jugadores
        if (reserva.esPartido()) {
            return Evaluacion.noCancelable();
        }

        if (reserva.getEstado() == EstadoReserva.PENDIENTE) {
            // sin dinero cobrado: se puede cancelar salvo que MP esté procesando un pago
            boolean cancelable = !reserva.tienePagoActivo() && !reserva.turnoEmpezo(ahora);
            return new Evaluacion(cancelable, cancelable ? inicio : null, BigDecimal.ZERO, null, null);
        }
        if (!Reserva.ESTADOS_CONFIRMADOS.contains(reserva.getEstado())
                || reserva.tienePagoEnProceso() || reserva.turnoEmpezo(ahora)) {
            return Evaluacion.noCancelable();
        }

        Configuracion config = configuracionService.obtener();
        LocalDateTime totalHasta = inicio.minusMinutes(config.getMinutosReembolsoTotal());
        LocalDateTime seniaHasta = inicio.minusMinutes(config.getMinutosReembolsoSenia());

        long minutosQueFaltan = Duration.between(ahora, inicio).toMinutes();
        OpcionReembolso opcion = minutosQueFaltan >= config.getMinutosReembolsoTotal() ? OpcionReembolso.TOTAL
                : minutosQueFaltan >= config.getMinutosReembolsoSenia() ? OpcionReembolso.SENIA
                        : OpcionReembolso.NADA;

        return new Evaluacion(true, inicio, montoSegun(reserva, opcion), totalHasta, seniaHasta);
    }

    /** Cuánto se devuelve con cada opción: todo lo pagado, hasta la seña, o nada. */
    public BigDecimal montoSegun(Reserva reserva, OpcionReembolso opcion) {
        BigDecimal pagado = reserva.getMontoPagado();
        return switch (opcion) {
            case TOTAL -> pagado;
            case SENIA -> pagado.min(configuracionService.calcularSenia(configuracionService.obtener(),
                    reserva.getMontoTotal()));
            case NADA -> BigDecimal.ZERO;
        };
    }
}
