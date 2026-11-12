package com.LosCiruelos.padel_club_api.pagos.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Qué se le devuelve al cliente de un cobro, línea por línea (para devolverlo todo, todas las líneas). */
@Data
public class DevolucionRequest {

    @NotEmpty(message = "Indicá al menos una línea a devolver")
    @Valid
    private List<LineaDevolucionRequest> lineas;

    @Size(max = 200, message = "El motivo puede tener hasta 200 caracteres")
    private String motivo;
}
