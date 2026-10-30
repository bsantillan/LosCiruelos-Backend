package com.LosCiruelos.padel_club_api.common;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Un turno: fecha y franja horaria [inicio, fin). Concentra las cuentas de horarios que antes se
 * repetían a mano (duración, superposición, si empezó o terminó).
 */
public record Turno(LocalDate fecha, LocalTime inicio, LocalTime fin) {

    public LocalDateTime inicioEnFecha() {
        return fecha.atTime(inicio);
    }

    public LocalDateTime finEnFecha() {
        return fecha.atTime(fin);
    }

    public long duracionMinutos() {
        return minutosEntre(inicio, fin);
    }

    /** Dos franjas se superponen si una empieza antes de que termine la otra (los bordes no cuentan). */
    public static boolean seSuperponen(LocalTime inicio, LocalTime fin, LocalTime otroInicio, LocalTime otroFin) {
        return inicio.isBefore(otroFin) && fin.isAfter(otroInicio);
    }

    /** Misma fecha y franjas superpuestas. */
    public boolean seSuperponeCon(Turno otro) {
        return fecha.equals(otro.fecha) && seSuperponen(inicio, fin, otro.inicio, otro.fin);
    }

    public boolean empezo(LocalDateTime ahora) {
        return !ahora.isBefore(inicioEnFecha());
    }

    public boolean termino(LocalDateTime ahora) {
        return !ahora.isBefore(finEnFecha());
    }

    public static long minutosEntre(LocalTime inicio, LocalTime fin) {
        return Duration.between(inicio, fin).toMinutes();
    }
}
