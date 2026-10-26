package com.LosCiruelos.padel_club_api.reservas.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Partido de 4 jugadores (ADMIN): al crearlo y al modificarlo se manda completo. */
@Data
public class PartidoRequest {

    @NotNull(message = "El ID de la cancha es obligatorio")
    private Long canchaId;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fechaReserva;

    @NotNull(message = "La hora de inicio es obligatoria")
    private LocalTime horaInicio;

    @NotNull(message = "La hora de fin es obligatoria")
    private LocalTime horaFin;

    // en orden: la posición de cada jugador es su lugar en la lista (1 a 4)
    @NotNull(message = "Indicá los 4 jugadores")
    @Size(min = 4, max = 4, message = "Un partido tiene 4 jugadores")
    private List<@Valid @NotNull(message = "Indicá los 4 jugadores") JugadorRequest> jugadores;

    @Min(value = 0, message = "La cantidad de pelotas no puede ser negativa")
    @Max(value = 50, message = "Se pueden pedir hasta 50 pelotas")
    private Integer cantidadPelotas;

    @Min(value = 0, message = "La cantidad de paletas no puede ser negativa")
    @Max(value = 20, message = "Se pueden pedir hasta 20 paletas")
    private Integer cantidadPaletas;
}
