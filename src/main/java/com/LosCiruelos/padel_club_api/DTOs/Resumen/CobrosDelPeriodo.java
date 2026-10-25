package com.LosCiruelos.padel_club_api.DTOs.Resumen;

import java.math.BigDecimal;

/**
 * De las reservas del período: la plata que el club se quedó (sin bonificaciones) y lo
 * bonificado, de los turnos jugados y de los por jugar, y lo que retuvo de las canceladas.
 */
public record CobrosDelPeriodo(BigDecimal cobradoJugados, BigDecimal bonificadoJugados,
        BigDecimal cobradoPorJugar, BigDecimal bonificadoPorJugar, BigDecimal retenidoCancelaciones) {
}
