package com.LosCiruelos.padel_club_api.reservas.dto.request;

import java.time.LocalDate;
import java.time.LocalTime;

import com.LosCiruelos.padel_club_api.pagos.dto.request.BonificacionRequest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

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

    // clienteId es opcional: si viene null, se usa el usuario autenticado.
    // Un ADMIN puede reservar en nombre de otro usuario.
    Long clienteId;

    // cantidad de pelotas que se compran (opcional, por defecto 0)
    @Min(value = 0, message = "La cantidad de pelotas no puede ser negativa")
    @Max(value = 50, message = "Se pueden pedir hasta 50 pelotas")
    Integer cantidadPelotas;

    // cantidad de paletas que se alquilan para el turno (opcional, por defecto 0)
    @Min(value = 0, message = "La cantidad de paletas no puede ser negativa")
    @Max(value = 20, message = "Se pueden pedir hasta 20 paletas")
    Integer cantidadPaletas;

    // Solo ADMIN (opcional): bonificar el turno al crearlo, todo o una parte
    @Valid
    BonificacionRequest bonificacion;
}
