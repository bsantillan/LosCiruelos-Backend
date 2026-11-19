package com.LosCiruelos.padel_club_api.reservas.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Partido de 1 a 4 jugadores (ADMIN): al crearlo y al modificarlo se manda completo. */
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

    // de 1 a 4, en orden: la posición de cada jugador es su lugar en la lista. El 1 (responsable) tiene
    // que ser un cliente registrado; del 2 al 4 son opcionales (clientes o invitados)
    @NotNull(message = "Indicá los jugadores")
    @Size(min = 1, max = 4, message = "Un partido tiene de 1 a 4 jugadores")
    private List<@Valid @NotNull(message = "Indicá los jugadores") JugadorRequest> jugadores;

    // productos del catálogo (opcional): cada uno con productoId y cantidad
    @Valid
    @Size(max = 20, message = "Se pueden pedir hasta 20 productos distintos")
    private List<ProductoPedidoRequest> productos;
}
