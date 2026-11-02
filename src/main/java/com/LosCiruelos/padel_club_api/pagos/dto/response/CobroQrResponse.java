package com.LosCiruelos.padel_club_api.pagos.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;

/**
 * Un cobro con QR dinámico.
 *
 * @param qrData         la trama del QR (EMVCo) que el front convierte en imagen
 * @param estado         EN_PROCESO mientras se espera el pago; APROBADO si se cobró; EXPIRADO si venció o
 *                       se canceló; A_REEMBOLSAR si se cobró otro monto o la reserva ya no estaba activa
 * @param montoPendiente lo que la reserva sigue debiendo
 */
public record CobroQrResponse(Long pagoId, String orderId, String qrData, BigDecimal monto, LocalDateTime venceAt,
        EstadoPago estado, EstadoReserva estadoReserva, BigDecimal montoPendiente) {
}
