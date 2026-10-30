package com.LosCiruelos.padel_club_api.usuarios.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailRequest {
    @NotBlank(message = "El email es obligatorio")
    private String email;
}
