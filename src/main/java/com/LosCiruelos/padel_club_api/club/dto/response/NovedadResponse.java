package com.LosCiruelos.padel_club_api.club.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.club.entity.enums.EstadoNovedad;

/**
 * Una novedad en el panel del ADMIN.
 *
 * @param imagenUrl          GET /archivos/{nombre} (null sin imagen), hasta 1600 px de lado
 * @param imagenMiniaturaUrl la misma imagen de hasta 480 px (null sin imagen)
 * @param creadoPor el nombre de quien la creó
 */
public record NovedadResponse(Long id, String titulo, String descripcion, String etiqueta, String imagenUrl,
        String imagenMiniaturaUrl, boolean publicada, LocalDateTime publicadaAt, LocalDate visibleHasta, EstadoNovedad estado, String creadoPor,
        LocalDateTime creadoAt, LocalDateTime actualizadoAt) {
}
