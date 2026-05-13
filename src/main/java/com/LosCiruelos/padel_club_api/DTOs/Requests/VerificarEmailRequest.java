package com.LosCiruelos.padel_club_api.DTOs.Requests;


import com.LosCiruelos.padel_club_api.Entities.Enum.TokenType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class VerificarEmailRequest {
    @NotBlank(message = "El email es obligatorio")
    private String email;

    @NotBlank(message = "El codigo es obligatorio")
    @Size(min = 6, max = 6, message = "El codigo debe tener 6 digitos")
    private String codigo;

    @NotNull(message = "El tipo de codigo es obligatorio")
    private TokenType tipo_codigo;
}
