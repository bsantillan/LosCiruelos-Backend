package com.LosCiruelos.padel_club_api.mapper;

import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.club.dto.response.NovedadPublicaResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.NovedadResponse;
import com.LosCiruelos.padel_club_api.club.entity.Novedad;
import com.LosCiruelos.padel_club_api.common.archivos.AlmacenDeImagenes;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;

/** Novedad → respuesta del panel (con el estado de hoy) o pública. */
@Component
public class NovedadMapper {

    public NovedadResponse aResponse(Novedad n, LocalDate hoy) {
        return new NovedadResponse(n.getId(), n.getTitulo(), n.getDescripcion(), n.getEtiqueta(),
                AlmacenDeImagenes.url(n.getImagen()), AlmacenDeImagenes.urlMiniatura(n.getImagen()),
                Boolean.TRUE.equals(n.getPublicada()), n.getPublicadaAt(),
                n.getVisibleHasta(), n.estado(hoy), nombre(n.getCreadoPor()), n.getCreatedAt(), n.getUpdatedAt());
    }

    public NovedadPublicaResponse aPublica(Novedad n) {
        return new NovedadPublicaResponse(n.getId(), n.getTitulo(), n.getDescripcion(), n.getEtiqueta(),
                AlmacenDeImagenes.url(n.getImagen()), AlmacenDeImagenes.urlMiniatura(n.getImagen()), n.getPublicadaAt(),
                n.getVisibleHasta());
    }

    private static String nombre(Usuario u) {
        if (u == null) {
            return null;
        }
        String nombre = ((u.getNombre() != null ? u.getNombre() : "") + " "
                + (u.getApellido() != null ? u.getApellido() : "")).trim();
        return nombre.isEmpty() ? u.getEmail() : nombre;
    }
}
