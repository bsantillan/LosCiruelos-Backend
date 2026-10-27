package com.LosCiruelos.padel_club_api.club.dto.response;

import lombok.Data;

@Data
public class CanchaResponse {
    private Long id;
    private Integer numero;
    private String descripcion;
}
