package com.LosCiruelos.padel_club_api.Config;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Zona horaria del club y formato de fechas de la API.
 *
 * Todas las fechas se guardan y se calculan en hora de Argentina. En el JSON:
 * <ul>
 * <li>fecha y hora ({@code LocalDateTime}): {@code 2026-10-17T18:15:00.123-03:00}, siempre con
 * milisegundos y el offset, para que el navegador no la interprete en otra zona;</li>
 * <li>hora ({@code LocalTime}): {@code 18:00};</li>
 * <li>fecha ({@code LocalDate}): {@code 2026-10-17} (el formato por defecto).</li>
 * </ul>
 * Los datos de entrada no cambian: horas {@code HH:mm} y fechas {@code yyyy-MM-dd}.
 */
public final class Fechas {

    public static final ZoneId ZONA = ZoneId.of("America/Argentina/Buenos_Aires");

    public static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
    public static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private Fechas() {
    }

    /** Fecha y hora del club con el offset de Argentina. */
    public static String formatear(LocalDateTime valor) {
        return FECHA_HORA.format(valor.atZone(ZONA).toOffsetDateTime());
    }

    /** Módulo de Jackson con los formatos de salida. */
    public static JacksonModule moduloJackson() {
        return new SimpleModule("fechas-argentina")
                .addSerializer(LocalDateTime.class, new StdSerializer<LocalDateTime>(LocalDateTime.class) {
                    @Override
                    public void serialize(LocalDateTime valor, JsonGenerator gen, SerializationContext ctx) {
                        gen.writeString(formatear(valor));
                    }
                })
                .addSerializer(LocalTime.class, new StdSerializer<LocalTime>(LocalTime.class) {
                    @Override
                    public void serialize(LocalTime valor, JsonGenerator gen, SerializationContext ctx) {
                        gen.writeString(HORA.format(valor));
                    }
                });
    }
}
