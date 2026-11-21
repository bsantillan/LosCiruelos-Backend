package com.LosCiruelos.padel_club_api.ventas.repository;

import java.math.BigDecimal;

/**
 * Ventas sueltas cobradas en un período: cuántas y lo cobrado en efectivo, con Mercado Pago (con el QR
 * de la caja, por transferencia y con tarjeta en su posnet), con Cuenta DNI y con tarjeta en otro posnet.
 * Aparte, lo bonificado por el ADMIN (no es plata: no suma al total).
 */
public record TotalesDeVentas(Long cantidad, BigDecimal efectivo, BigDecimal mercadoPagoQr, BigDecimal transferencia,
        BigDecimal tarjetaMp, BigDecimal cuentaDni, BigDecimal tarjeta, BigDecimal bonificado) {

    /** Todo lo de Mercado Pago: QR, transferencia y tarjeta en su posnet. */
    public BigDecimal mercadoPago() {
        return mercadoPagoQr.add(transferencia).add(tarjetaMp);
    }

    public BigDecimal total() {
        return efectivo.add(mercadoPago()).add(cuentaDni).add(tarjeta);
    }
}
