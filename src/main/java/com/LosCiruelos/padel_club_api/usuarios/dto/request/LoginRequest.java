package com.LosCiruelos.padel_club_api.usuarios.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank
    private String email;
    
    @NotBlank
    private String password;
}
