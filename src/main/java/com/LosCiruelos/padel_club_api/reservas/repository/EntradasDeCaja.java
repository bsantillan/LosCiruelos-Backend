package com.LosCiruelos.padel_club_api.reservas.repository;

import java.math.BigDecimal;

/**
 * Plata cobrada en el período (por fecha de pago), por medio: efectivo, Mercado Pago (online, con el
 * QR de la caja y por transferencia) y Cuenta DNI. Aparte, lo bonificado (no es plata).
 */
public record EntradasDeCaja(BigDecimal efectivo, BigDecimal mercadoPagoOnline, BigDecimal mercadoPagoQr,
        BigDecimal transferencia, BigDecimal cuentaDni, BigDecimal bonificado) {

    /** Todo lo de Mercado Pago: online, QR y transferencia. */
    public BigDecimal mercadoPago() {
        return mercadoPagoOnline.add(mercadoPagoQr).add(transferencia);
    }
}
