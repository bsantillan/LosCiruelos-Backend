package com.LosCiruelos.padel_club_api.club.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * La jornada de trabajo en curso: lo que el EMPLEADO puede ver y operar.
 *
 * @param fecha  el día de la jornada (el de sus turnos)
 * @param inicio la apertura de ese día
 * @param fin    la apertura del día siguiente (sin incluir)
 */
public record JornadaResponse(LocalDate fecha, LocalDateTime inicio, LocalDateTime fin) {
}
