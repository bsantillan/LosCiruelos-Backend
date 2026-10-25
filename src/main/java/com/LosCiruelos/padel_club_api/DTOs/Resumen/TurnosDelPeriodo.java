package com.LosCiruelos.padel_club_api.DTOs.Resumen;

import java.math.BigDecimal;

/**
 * Reservas confirmadas del período separadas por si el turno ya terminó (jugados) o no (por
 * jugar), con la suma de sus montos, y las canceladas. Sin reservas, las sumas vienen en null.
 */
public record TurnosDelPeriodo(Long jugados, BigDecimal montoJugados, Long porJugar, BigDecimal montoPorJugar,
        Long cancelados) {
}
