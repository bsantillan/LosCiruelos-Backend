package com.LosCiruelos.padel_club_api.usuarios.dto.request;

import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Posicion;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @Email(message = "Email inválido")
    @NotBlank(message = "El email es obligatorio")
    private String email;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 50, message = "El nombre debe tener entre 2 y 50 caracteres")
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(min = 2, max = 50, message = "El apellido debe tener entre 2 y 50 caracteres")
    private String apellido;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 8, max = 30, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    @NotNull(message = "Debes aceptar los términos y condiciones")
    private Boolean termsAccepted;

    @NotBlank(message = "El telefono es obligatorio")
    // se acepta con espacios, guiones o paréntesis: el servidor lo normaliza al formato de WhatsApp
    @Pattern(regexp = "^[0-9+() -]{8,25}$", message = "Teléfono inválido")
    private String telefono;

    // "Acepto recibir notificaciones del club" (obligatorio, como los términos). Arrancan por mail:
    // el medio (mail o WhatsApp) se elige en el perfil, después de validar el celular
    @NotNull(message = "Debés aceptar recibir notificaciones")
    @AssertTrue(message = "Debés aceptar recibir notificaciones")
    private Boolean aceptarNotificacion;

    @NotNull(message = "La categoria es obligatoria")
    private Categoria categoria;

    @NotNull(message = "La posicion es obligatoria")
    private Posicion posicion;
}
