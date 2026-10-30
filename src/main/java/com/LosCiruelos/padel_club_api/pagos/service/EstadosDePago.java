package com.LosCiruelos.padel_club_api.pagos.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.notificaciones.aviso.AvisoReserva;
import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.service.StockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lo que comparten los pagos online, los cobros en el club y los reembolsos: el cambio de estado
 * de un pago, el estado de la reserva según lo pagado (con sus efectos: stock y avisos) y los
 * avisos de reembolso. Corre dentro de la transacción de quien lo llama, con la reserva ya
 * bloqueada.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class EstadosDePago {

    private final PagoRepository pagoRepository;
    private final ConfiguracionService configuracionService;
    private final StockService stockService;
    private final Reloj reloj;
    private final ApplicationEventPublisher eventos;

    void cambiar(Pago pago, EstadoPago estado) {
        pago.cambiarEstado(estado, reloj.ahora());
        pagoRepository.save(pago);
    }

    /** {@link #recalcularReserva(Reserva, boolean)} con avisos. */
    void recalcularReserva(Reserva reserva) {
        recalcularReserva(reserva, true);
    }

    /**
     * Recalcula el estado de la reserva con lo pagado ({@link Reserva#recalcularEstado}). Si queda
     * CANCELADA (se devolvió todo) se descartan los pagos iniciados y se libera el stock.
     *
     * @param avisar false cuando el cambio lo hizo el ADMIN mismo (bonificación)
     */
    void recalcularReserva(Reserva reserva, boolean avisar) {
        EstadoReserva anterior = reserva.getEstado();
        BigDecimal senia = configuracionService.obtener().senia(reserva.getMontoTotal());
        if (!reserva.recalcularEstado(senia, reloj.ahora())) {
            return;
        }
        EstadoReserva nuevo = reserva.getEstado();
        if (nuevo == EstadoReserva.CANCELADA) {
            reserva.expirarPagosPendientes().forEach(pagoRepository::save);
            stockService.liberar(reserva);
            log.warn("Reserva #{} cancelada: se devolvió todo lo pagado", reserva.getId());
        }
        log.info("Reserva #{}: {} → {}", reserva.getId(), anterior, nuevo);
        // los partidos los arma y los sigue el ADMIN: no se le avisa de cada pago
        if (avisar && !reserva.esPartido()) {
            avisarCambioDeEstado(reserva, anterior, nuevo);
        }
    }

    void validarQueAdmitePagos(Reserva reserva) {
        LocalDateTime ahora = reloj.ahora();
        if (reserva.estaVencida(ahora)) {
            throw new EstadoInvalidoException("El tiempo para pagar esta reserva expiró.");
        }
        if (!reserva.admitePagos(ahora)) {
            throw new EstadoInvalidoException("La reserva no admite pagos en estado " + reserva.getEstado() + ".");
        }
    }

    /** Un reembolso que había quedado pendiente al cancelar finalmente se hizo. */
    void avisarReembolsoRealizado(Reserva reserva, BigDecimal monto) {
        eventos.publishEvent(AvisoReserva.de(TipoAviso.REEMBOLSO_REALIZADO, reserva, monto, null));
    }

    /** Hay que devolver a mano lo que no se pudo reembolsar por Mercado Pago. */
    void avisarReembolsoManual(Pago pago, Reserva reserva, String motivo) {
        BigDecimal monto = pago.getMontoReembolsado() != null ? pago.getMontoReembolsado() : pago.getMonto();
        eventos.publishEvent(AvisoReserva.de(TipoAviso.REEMBOLSO_MANUAL, reserva, monto, motivo));
    }

    /**
     * Avisos a los ADMIN: reserva confirmada (primera vez que llega a la seña o al total),
     * saldo cobrado (una señada queda paga) o cancelada por devolución de todo lo pagado.
     */
    private void avisarCambioDeEstado(Reserva reserva, EstadoReserva anterior, EstadoReserva nuevo) {
        BigDecimal pagado = reserva.getMontoCubierto();
        boolean eraConfirmada = anterior == EstadoReserva.SEÑADA || anterior == EstadoReserva.PAGADA;
        boolean quedaConfirmada = nuevo == EstadoReserva.SEÑADA || nuevo == EstadoReserva.PAGADA
                || nuevo == EstadoReserva.COMPLETADA;
        boolean quedaPaga = nuevo == EstadoReserva.PAGADA || nuevo == EstadoReserva.COMPLETADA;

        if (quedaConfirmada && !eraConfirmada) {
            eventos.publishEvent(AvisoReserva.de(TipoAviso.RESERVA_CONFIRMADA, reserva, pagado, null));
        } else if (quedaPaga && anterior == EstadoReserva.SEÑADA) {
            eventos.publishEvent(AvisoReserva.de(TipoAviso.SALDO_COBRADO, reserva, pagado, null));
        } else if (nuevo == EstadoReserva.CANCELADA) {
            eventos.publishEvent(AvisoReserva.de(TipoAviso.RESERVA_CANCELADA, reserva, BigDecimal.ZERO,
                    "Se devolvió todo lo pagado desde Mercado Pago."));
        }
    }
}
