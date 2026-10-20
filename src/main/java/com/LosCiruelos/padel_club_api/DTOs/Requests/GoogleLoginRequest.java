package com.LosCiruelos.padel_club_api.DTOs.Requests;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GoogleLoginRequest {
    @NotBlank(message = "El idToken es obligatorio")
    private String idToken;
}