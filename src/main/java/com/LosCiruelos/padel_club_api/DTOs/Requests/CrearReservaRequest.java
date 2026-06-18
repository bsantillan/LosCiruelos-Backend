package com.LosCiruelos.padel_club_api.DTOs.Requests;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CrearReservaRequest {
    @NotNull(message = "El ID de la cancha es obligatorio")
    Integer canchaId;

    @NotNull(message = "La fecha de reserva es obligatoria")
    @FutureOrPresent(message = "La fecha debe ser hoy o en el futuro")
    LocalDate fechaReserva;

    @NotNull(message = "La hora de inicio es obligatoria")
    LocalTime horaInicio;

    @NotNull(message = "La hora de fin es obligatoria")
    LocalTime horaFin;

    // clienteId es opcional: si viene null, se usa el usuario autenticado.
    // Un ADMIN puede reservar en nombre de otro usuario.
    Long clienteId;

    @NotNull(message = "El uso de pelotas es obligatorio")
    Boolean pelotas;

    @NotNull(message = "El uso de paletas es obligatorio")
    Boolean paletas;
}
