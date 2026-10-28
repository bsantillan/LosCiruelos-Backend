package com.LosCiruelos.padel_club_api.notificaciones.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Avisos opcionales que elige el cliente. Las cancelaciones se avisan siempre. */
@Data
public class SuscripcionesRequest {
    @NotNull(message = "Es obligatorio")
    private Boolean recordatorios;

    @NotNull(message = "Es obligatorio")
    private Boolean promociones;
}
