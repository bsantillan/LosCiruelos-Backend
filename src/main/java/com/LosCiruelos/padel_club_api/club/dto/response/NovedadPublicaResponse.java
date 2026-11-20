package com.LosCiruelos.padel_club_api.club.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Una novedad publicada, para la página de inicio (GET /novedades).
 *
 * @param imagenMiniaturaUrl la imagen de hasta 480 px, para las tarjetas (null sin imagen)
 */
public record NovedadPublicaResponse(Long id, String titulo, String descripcion, String etiqueta, String imagenUrl,
        String imagenMiniaturaUrl, LocalDateTime publicadaAt, LocalDate visibleHasta) {
}
