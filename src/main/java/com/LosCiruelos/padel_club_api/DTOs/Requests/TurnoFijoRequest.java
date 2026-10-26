package com.LosCiruelos.padel_club_api.DTOs.Requests;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Turno fijo (ADMIN): se juega todas las semanas el mismo día que {@code desde}, en esa cancha y
 * ese horario, con esos jugadores. Al crearlo y al modificarlo se manda completo.
 */
@Data
public class TurnoFijoRequest {

    @NotNull(message = "El ID de la cancha es obligatorio")
    private Long canchaId;

    // primera fecha que se juega: define el día de la semana
    @NotNull(message = "La fecha desde es obligatoria")
    private LocalDate desde;

    @NotNull(message = "La hora de inicio es obligatoria")
    private LocalTime horaInicio;

    @NotNull(message = "La hora de fin es obligatoria")
    private LocalTime horaFin;

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
