package com.LosCiruelos.padel_club_api.reservas.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Reserva que pide el cliente para sí (POST /reservas). */
@Data
public class CrearReservaRequest {
    @NotNull(message = "El ID de la cancha es obligatorio")
    Long canchaId;

    @NotNull(message = "La fecha de reserva es obligatoria")
    @FutureOrPresent(message = "La fecha debe ser hoy o en el futuro")
    LocalDate fechaReserva;

    @NotNull(message = "La hora de inicio es obligatoria")
    LocalTime horaInicio;

    @NotNull(message = "La hora de fin es obligatoria")
    LocalTime horaFin;

    // productos del catálogo (opcional): cada uno con productoId y cantidad
    @Valid
    @Size(max = 20, message = "Se pueden pedir hasta 20 productos distintos")
    List<ProductoPedidoRequest> productos;
}
