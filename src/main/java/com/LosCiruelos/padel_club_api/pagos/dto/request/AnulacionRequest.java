package com.LosCiruelos.padel_club_api.pagos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Por qué se anula un cobro (un error de carga). */
@Data
public class AnulacionRequest {
    @NotBlank(message = "Indicá el motivo de la anulación")
    @Size(max = 200, message = "El motivo puede tener hasta 200 caracteres")
    private String motivo;
}
