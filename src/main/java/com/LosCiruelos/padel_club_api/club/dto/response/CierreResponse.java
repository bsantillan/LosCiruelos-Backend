package com.LosCiruelos.padel_club_api.club.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un día cerrado.
 *
 * @param cancelaciones solo al cerrarlo: las reservas de ese día que se cancelaron (null en el listado)
 */
public record CierreResponse(LocalDate fecha, String motivo, LocalDateTime cerradoAt,
        CancelacionesResponse cancelaciones) {
}
