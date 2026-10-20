package com.LosCiruelos.padel_club_api.DTOs.Responses;

import com.LosCiruelos.padel_club_api.Entities.Cancha;

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
