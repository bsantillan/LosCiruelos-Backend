package com.LosCiruelos.padel_club_api.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * La hora del club (Argentina). Todo el backend toma "ahora" de acá: nunca de
 * {@code LocalDateTime.now()} sin reloj. En los tests se usa {@link #fijo(LocalDateTime)}.
 */
public class Reloj {

    private final Clock clock;

    public Reloj(Clock clock) {
        this.clock = clock;
    }

    /** Reloj del sistema en la zona del club, con precisión de milisegundos (lo que guarda la base). */
    public static Reloj delSistema() {
        return new Reloj(Clock.tick(Clock.system(Fechas.ZONA), java.time.Duration.ofMillis(1)));
    }

    /** Reloj detenido en ese momento (hora de Argentina), para los tests. */
    public static Reloj fijo(LocalDateTime momento) {
        return new Reloj(Clock.fixed(momento.atZone(Fechas.ZONA).toInstant(), Fechas.ZONA));
    }

    public LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }

    public LocalDate hoy() {
        return LocalDate.now(clock);
    }

    public LocalTime horaActual() {
        return LocalTime.now(clock);
    }

    public Instant instante() {
        return clock.instant();
    }

    public long milisegundos() {
        return clock.millis();
    }
}
