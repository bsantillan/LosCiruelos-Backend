package com.LosCiruelos.padel_club_api.DTOs.Requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token requerido")
    private String refreshToken;
}
