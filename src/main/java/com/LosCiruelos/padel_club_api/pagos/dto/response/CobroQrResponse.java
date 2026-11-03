package com.LosCiruelos.padel_club_api.pagos.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;

/**
 * Un cobro con el QR de la caja: el cliente escanea el QR impreso del mostrador y paga este monto.
 *
 * @param estado         EN_PROCESO mientras se espera el pago; APROBADO si se cobró; EXPIRADO si venció o
 *                       se canceló; A_REEMBOLSAR si se cobró otro monto o la reserva ya no estaba activa
 * @param montoPendiente lo que la reserva sigue debiendo
 */
public record CobroQrResponse(Long pagoId, String orderId, BigDecimal monto, LocalDateTime venceAt,
        EstadoPago estado, EstadoReserva estadoReserva, BigDecimal montoPendiente) {
}
