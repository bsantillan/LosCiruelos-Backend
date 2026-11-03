package com.LosCiruelos.padel_club_api.pagos.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Un cobro con Cuenta DNI para controlar contra la cuenta del club: el pago de una reserva o una
 * venta suelta.
 *
 * @param id        id del pago (origen RESERVA) o de la venta (origen VENTA): es el que se usa para verificarlo
 * @param reservaId la reserva del pago (null en una venta)
 */
public record CobroCuentaDniResponse(Origen origen, Long id, Long reservaId, BigDecimal monto, LocalDateTime fecha,
        boolean verificado, LocalDateTime verificadoAt) {

    public enum Origen {
        RESERVA, // se verifica con PUT /admin/reservas/{reservaId}/pagos/{id}/verificacion
        VENTA    // se verifica con PUT /admin/ventas/{id}/verificacion
    }
}
