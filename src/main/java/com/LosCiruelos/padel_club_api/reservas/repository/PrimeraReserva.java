package com.LosCiruelos.padel_club_api.reservas.repository;

import java.time.LocalDate;

/** La fecha de la primera reserva confirmada de un cliente (para saber si es nuevo). */
public record PrimeraReserva(Long clienteId, LocalDate fecha) {
}
