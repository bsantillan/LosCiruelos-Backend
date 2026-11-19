package com.LosCiruelos.padel_club_api.common;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * La jornada de trabajo del día {@code fecha}: desde la apertura de ese día hasta la apertura del
 * día siguiente (según el horario semanal). Pasada la medianoche sigue siendo la jornada del día
 * anterior hasta que el club vuelve a abrir. La calcula {@code Configuracion.jornadaEn}.
 *
 * @param fecha  el día al que pertenece la jornada (el de sus turnos)
 * @param inicio la apertura de ese día (inclusive)
 * @param fin    la apertura del día siguiente (exclusive)
 */
public record Jornada(LocalDate fecha, LocalDateTime inicio, LocalDateTime fin) {

    /** {@code momento} cae dentro de la jornada. */
    public boolean incluye(LocalDateTime momento) {
        return momento != null && !momento.isBefore(inicio) && momento.isBefore(fin);
    }

    /** La fecha es la de esta jornada o una posterior (lo que el EMPLEADO puede ver y operar). */
    public boolean noEsAnteriorA(LocalDate otra) {
        return !otra.isBefore(fecha);
    }
}
