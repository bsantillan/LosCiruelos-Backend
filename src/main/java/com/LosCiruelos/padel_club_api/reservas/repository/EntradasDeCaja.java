package com.LosCiruelos.padel_club_api.reservas.repository;

import java.math.BigDecimal;

/**
 * Plata cobrada en el período (por fecha de pago), por medio: efectivo, Mercado Pago (online, con el
 * QR de la caja, por transferencia y con tarjeta en su posnet), Cuenta DNI y tarjeta en otro posnet.
 * Aparte, lo bonificado (no es plata).
 */
public record EntradasDeCaja(BigDecimal efectivo, BigDecimal mercadoPagoOnline, BigDecimal mercadoPagoQr,
        BigDecimal transferencia, BigDecimal tarjetaMp, BigDecimal cuentaDni, BigDecimal tarjeta,
        BigDecimal bonificado) {

    /** Todo lo de Mercado Pago: online, QR, transferencia y tarjeta en su posnet. */
    public BigDecimal mercadoPago() {
        return mercadoPagoOnline.add(mercadoPagoQr).add(transferencia).add(tarjetaMp);
    }
}
