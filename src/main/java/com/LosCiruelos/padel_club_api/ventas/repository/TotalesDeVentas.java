package com.LosCiruelos.padel_club_api.ventas.repository;

import java.math.BigDecimal;

/** Ventas sueltas de un período: cuántas y lo cobrado en efectivo y con Mercado Pago en el club. */
public record TotalesDeVentas(Long cantidad, BigDecimal efectivo, BigDecimal mercadoPago) {

    public BigDecimal total() {
        return efectivo.add(mercadoPago);
    }
}
