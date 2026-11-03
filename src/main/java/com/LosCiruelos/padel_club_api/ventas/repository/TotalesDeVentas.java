package com.LosCiruelos.padel_club_api.ventas.repository;

import java.math.BigDecimal;

/** Ventas sueltas de un período: cuántas y lo cobrado en efectivo, con Mercado Pago y con Cuenta DNI. */
public record TotalesDeVentas(Long cantidad, BigDecimal efectivo, BigDecimal mercadoPago, BigDecimal cuentaDni) {

    public BigDecimal total() {
        return efectivo.add(mercadoPago).add(cuentaDni);
    }
}
