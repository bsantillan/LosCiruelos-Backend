package com.LosCiruelos.padel_club_api.usuarios.dto.request;

import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Posicion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

import lombok.Data;

@Data
public class FiltroUsuarioRequest {
    private Role rol;
    private Boolean enabled;
    private String nombre;
    private String email;
    private Categoria categoria;
    private Posicion posicion;
}
