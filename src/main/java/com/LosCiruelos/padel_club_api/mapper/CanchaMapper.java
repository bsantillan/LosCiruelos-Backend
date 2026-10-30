package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.club.dto.response.CanchaResponse;
import com.LosCiruelos.padel_club_api.club.entity.Cancha;

/** Cancha → CanchaResponse. */
@Component
public class CanchaMapper {

    public CanchaResponse aResponse(Cancha cancha) {
        CanchaResponse response = new CanchaResponse();
        response.setId(cancha.getId());
        response.setNumero(cancha.getNumero());
        response.setDescripcion(cancha.getDescripcion());
        return response;
    }
}
