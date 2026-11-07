package com.LosCiruelos.padel_club_api.ventas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Por qué se anula una venta (queda registrado junto a quién la anuló). */
@Data
public class AnulacionVentaRequest {
    @NotBlank(message = "Indicá por qué se anula la venta")
    @Size(max = 200, message = "El motivo puede tener hasta 200 caracteres")
    private String motivo;
}
