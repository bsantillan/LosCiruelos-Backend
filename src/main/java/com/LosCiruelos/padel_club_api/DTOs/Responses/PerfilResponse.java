package com.LosCiruelos.padel_club_api.DTOs.Responses;

import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.Entities.Enum.Categoria;
import com.LosCiruelos.padel_club_api.Entities.Enum.Posicion;

import lombok.Data;

@Data
public class PerfilResponse {
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private Categoria categoria;
    private Posicion posicion;
    private Integer cantDiasMiembro;
    private Integer cantPartidos;
    private Integer diasDesdeUltimoPartido;
    private Long cantPartidosEsteMes;
    private LocalDateTime categoriaActualizadaAt;
}
