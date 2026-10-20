package com.LosCiruelos.padel_club_api.DTOs.Requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "El email es obligatorio")
    private String email;

    @NotBlank(message = "La nueva contraseña es obligatoria")
    private String nuevaPassword;
}
