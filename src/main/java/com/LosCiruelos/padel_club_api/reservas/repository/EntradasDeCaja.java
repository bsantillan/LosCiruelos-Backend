package com.LosCiruelos.padel_club_api.reservas.repository;

import java.math.BigDecimal;

/** Plata cobrada en el período (por fecha de pago), por medio: efectivo, Mercado Pago y Cuenta DNI. */
public record EntradasDeCaja(BigDecimal efectivo, BigDecimal mercadoPago, BigDecimal cuentaDni) {
}
