package com.LosCiruelos.padel_club_api.DTOs.Requests;

import com.LosCiruelos.padel_club_api.Entities.Enum.Categoria;
import com.LosCiruelos.padel_club_api.Entities.Enum.Posicion;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;

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
