package com.LosCiruelos.padel_club_api.club.dto.response;

import com.LosCiruelos.padel_club_api.club.entity.Cancha;

import lombok.Data;

@Data
public class CanchaResponse {
    private Long id;
    private Integer numero;
    private String descripcion;

    public static CanchaResponse from(Cancha cancha) {
        CanchaResponse response = new CanchaResponse();
        response.setId(cancha.getId());
        response.setNumero(cancha.getNumero());
        response.setDescripcion(cancha.getDescripcion());
        return response;
    }
}
