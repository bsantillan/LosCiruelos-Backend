package com.LosCiruelos.padel_club_api.DTOs.Resumen;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;

/** Cantidad de reservas en ese estado, suma de sus montos y cuántas tienen el turno terminado. */
public record ReservasPorEstado(EstadoReserva estado, Long cantidad, BigDecimal montoTotal, Long terminadas) {
}
