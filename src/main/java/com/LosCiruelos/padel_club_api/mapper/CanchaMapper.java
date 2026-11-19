package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.club.dto.response.CanchaResponse;
import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.common.archivos.AlmacenDeImagenes;

/** Cancha → CanchaResponse (con sus fotos en orden). */
@Component
public class CanchaMapper {

    public CanchaResponse aResponse(Cancha cancha) {
        CanchaResponse response = new CanchaResponse();
        response.setId(cancha.getId());
        response.setNumero(cancha.getNumero());
        response.setNombre(cancha.getNombre());
        response.setDescripcion(cancha.getDescripcion());
        response.setTipo(cancha.getTipo());
        response.setSuperficie(cancha.getSuperficie());
        response.setIluminacion(cancha.getIluminacion());
        response.setActiva(cancha.getActiva());
        response.setFotos(cancha.getFotos().stream()
                .map(f -> new CanchaResponse.Foto(f.getId(), AlmacenDeImagenes.url(f.getArchivo()), f.getOrden()))
                .toList());
        return response;
    }

    /** Para el panel del ADMIN: con si se puede eliminar. */
    public CanchaResponse aResponseAdmin(Cancha cancha, boolean eliminable) {
        CanchaResponse response = aResponse(cancha);
        response.setEliminable(eliminable);
        return response;
    }
}
