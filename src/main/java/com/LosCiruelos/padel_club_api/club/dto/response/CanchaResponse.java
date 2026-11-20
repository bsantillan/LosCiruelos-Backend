package com.LosCiruelos.padel_club_api.club.dto.response;

import java.util.List;

import com.LosCiruelos.padel_club_api.club.entity.enums.TipoCancha;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

@Data
public class CanchaResponse {
    private Long id;
    private Integer numero;
    private String nombre;
    private String descripcion;
    private TipoCancha tipo;
    private String superficie;
    private Boolean iluminacion;
    private Boolean activa;
    // en orden
    private List<Foto> fotos;
    // solo en GET /admin/canchas: true si nunca se usó (se puede eliminar)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean eliminable;

    /**
     * @param url          pública, GET /archivos/{nombre}: hasta 1600 px de lado
     * @param miniaturaUrl la misma foto de hasta 480 px, para tarjetas y listados
     */
    public record Foto(Long id, String url, String miniaturaUrl, Integer orden) {
    }
}
