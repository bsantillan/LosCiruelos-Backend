package com.LosCiruelos.padel_club_api.DTOs.Responses;

import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Categoria;
import com.LosCiruelos.padel_club_api.Entities.Enum.Posicion;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;

import lombok.Data;

@Data
public class UsuarioAdminResponse {
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private Role rol;
    private Boolean enabled;
    private Boolean emailVerificado;
    private AuthProvider provider;
    // Solo para CLIENTE
    private Categoria categoria;
    private Posicion posicion;
}
