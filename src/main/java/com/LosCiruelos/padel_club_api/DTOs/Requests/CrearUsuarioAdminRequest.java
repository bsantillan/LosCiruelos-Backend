package com.LosCiruelos.padel_club_api.DTOs.Requests;

import com.LosCiruelos.padel_club_api.Entities.Enum.Categoria;
import com.LosCiruelos.padel_club_api.Entities.Enum.Posicion;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CrearUsuarioAdminRequest {

    @Email(message = "Email inválido")
    @NotBlank(message = "El email es obligatorio")
    private String email;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 50)
    private String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(min = 2, max = 50)
    private String apellido;

    @NotBlank(message = "El teléfono es obligatorio")
    @Pattern(regexp = "^\\+?[0-9]{8,15}$", message = "Teléfono inválido")
    private String telefono;

    @NotNull(message = "El rol es obligatorio")
    private Role rol;

    // Solo obligatorios si el rol es CLIENTE
    private Categoria categoria;
    private Posicion posicion;
}