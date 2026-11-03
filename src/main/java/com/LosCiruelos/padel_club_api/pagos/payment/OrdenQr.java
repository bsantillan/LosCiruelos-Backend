package com.LosCiruelos.padel_club_api.pagos.payment;

import java.math.BigDecimal;

/**
 * Lo que el proveedor del QR necesita de un cobro, venga de una reserva o de una venta suelta.
 *
 * @param orderId    la order en el proveedor (null antes de crearla)
 * @param referencia external_reference: solo ids internos, sin datos personales
 * @param clave      base de las claves de idempotencia (no cambia en toda la vida del cobro)
 * @param monto      lo que se cobra con la order
 */
public record OrdenQr(String orderId, String referencia, String clave, BigDecimal monto) {
}
