package com.LosCiruelos.padel_club_api.reservas.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
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

    // productos del catálogo (opcional): cada uno con productoId y cantidad
    @Valid
    @Size(max = 20, message = "Se pueden pedir hasta 20 productos distintos")
    private List<ProductoPedidoRequest> productos;
}
