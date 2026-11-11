package com.LosCiruelos.padel_club_api.pagos.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un cobro a mano para controlar contra la cuenta del club (Cuenta DNI o transferencia de Mercado
 * Pago): el pago de una reserva o una venta suelta.
 *
 * @param medio     CUENTA_DNI o TRANSFERENCIA_MP
 * @param id        id del pago (origen RESERVA) o de la venta (origen VENTA): es el que se usa para verificarlo
 * @param reservaId la reserva del pago (null en una venta)
 * @param anulada   venta anulada: la plata, si entró, se devolvió a mano (siempre false en una reserva)
 */
public record CobroCuentaDniResponse(Origen origen, Long id, Long reservaId,
        com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago medio, BigDecimal monto, LocalDateTime fecha,
        boolean verificado, LocalDateTime verificadoAt, boolean anulada) {

    public enum Origen {
        RESERVA, // se verifica con PUT /admin/reservas/{reservaId}/pagos/{id}/verificacion
        VENTA    // se verifica con PUT /admin/ventas/{id}/verificacion
    }
}
