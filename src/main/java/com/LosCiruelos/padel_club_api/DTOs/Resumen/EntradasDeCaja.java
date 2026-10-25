package com.LosCiruelos.padel_club_api.DTOs.Resumen;

import java.math.BigDecimal;

/** Plata cobrada en el período (por fecha de pago), por medio: efectivo, MP online y MP en el mostrador. */
public record EntradasDeCaja(BigDecimal efectivo, BigDecimal mercadoPagoOnline, BigDecimal mercadoPagoPresencial) {
}
