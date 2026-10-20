package com.LosCiruelos.padel_club_api.DTOs.Requests;

import com.LosCiruelos.padel_club_api.Entities.Enum.TokenType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReenviarCodigoRequest {
    @NotBlank(message = "El email es obligatorio")
    private String email;

    @NotNull(message = "El tipo de codigo es obligatorio")
    private TokenType tipo_codigo;
}
