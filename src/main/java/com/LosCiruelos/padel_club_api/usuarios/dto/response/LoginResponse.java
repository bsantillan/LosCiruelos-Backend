package com.LosCiruelos.padel_club_api.usuarios.dto.response;

import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

import lombok.Data;

@Data
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private Boolean perfilCompleto;
    private Role rol;
}
