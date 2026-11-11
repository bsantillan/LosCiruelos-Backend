package com.LosCiruelos.padel_club_api.ventas.repository;

import java.math.BigDecimal;

/**
 * Ventas sueltas cobradas en un período: cuántas y lo cobrado en efectivo, con Mercado Pago (con el QR
 * de la caja y por transferencia) y con Cuenta DNI.
 */
public record TotalesDeVentas(Long cantidad, BigDecimal efectivo, BigDecimal mercadoPagoQr, BigDecimal transferencia,
        BigDecimal cuentaDni) {

    /** Todo lo de Mercado Pago: QR y transferencia. */
    public BigDecimal mercadoPago() {
        return mercadoPagoQr.add(transferencia);
    }

    public BigDecimal total() {
        return efectivo.add(mercadoPago()).add(cuentaDni);
    }
}
