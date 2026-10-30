package com.LosCiruelos.padel_club_api.usuarios.dto.response;

import com.LosCiruelos.padel_club_api.usuarios.entity.enums.AuthProvider;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Posicion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

import lombok.Data;

@Data
public class UsuarioAdminResponse {
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private Boolean telefonoVerificado;
    private CanalNotificacion canalNotificaciones;
    private Role rol;
    private Boolean enabled;
    private Boolean emailVerificado;
    private AuthProvider provider;
    // Solo para CLIENTE
    private Categoria categoria;
    private Posicion posicion;
}
