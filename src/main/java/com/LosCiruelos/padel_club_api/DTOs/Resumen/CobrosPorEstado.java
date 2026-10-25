package com.LosCiruelos.padel_club_api.DTOs.Resumen;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;

/**
 * Lo que el club se quedó de los pagos de las reservas en ese estado: online y en el club, y de
 * eso, cuánto en efectivo (el resto es Mercado Pago, online o en el mostrador). Aparte, lo
 * bonificado por el ADMIN, que no es plata.
 */
public record CobrosPorEstado(EstadoReserva estado, BigDecimal online, BigDecimal enClub, BigDecimal efectivo,
        BigDecimal bonificado) {
}
