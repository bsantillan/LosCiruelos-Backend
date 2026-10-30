package com.LosCiruelos.padel_club_api.usuarios.dto.request;

import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Posicion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EditarUsuarioAdminRequest {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 50)
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(min = 2, max = 50)
    private String apellido;

    @NotBlank(message = "El telefono es obligatorio")
    // se acepta con espacios, guiones o paréntesis: el servidor lo normaliza al formato de WhatsApp
    @Pattern(regexp = "^[0-9+() -]{8,25}$", message = "Teléfono inválido")
    private String telefono;

    @NotNull(message = "El rol es obligatorio")
    private Role rol;

    // Solo para CLIENTE
    private Posicion posicion;
    private Categoria categoria;
}
