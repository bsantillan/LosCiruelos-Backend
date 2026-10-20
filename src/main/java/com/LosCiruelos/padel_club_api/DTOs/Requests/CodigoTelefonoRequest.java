package com.LosCiruelos.padel_club_api.DTOs.Requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Código de verificación del celular (perfil, con sesión). */
@Data
public class CodigoTelefonoRequest {

    @NotBlank(message = "El codigo es obligatorio")
    @Pattern(regexp = "^[0-9]{6}$", message = "El codigo debe tener 6 digitos")
    private String codigo;
}
