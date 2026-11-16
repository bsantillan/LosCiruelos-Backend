package com.LosCiruelos.padel_club_api.club.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Una novedad publicada, para la página de inicio (GET /novedades). */
public record NovedadPublicaResponse(Long id, String titulo, String descripcion, String etiqueta, String imagenUrl,
        LocalDateTime publicadaAt, LocalDate visibleHasta) {
}
