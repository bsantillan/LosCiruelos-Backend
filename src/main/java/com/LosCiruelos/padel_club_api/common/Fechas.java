package com.LosCiruelos.padel_club_api.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

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

    public static final Locale ES_AR = Locale.of("es", "AR");

    // para textos de avisos: "miércoles 14/10"
    private static final DateTimeFormatter FECHA_TEXTO = DateTimeFormatter.ofPattern("EEEE d/M", ES_AR);

    private Fechas() {
    }

    /** Fecha y hora del club con el offset de Argentina. */
    public static String formatear(LocalDateTime valor) {
        return FECHA_HORA.format(valor.atZone(ZONA).toOffsetDateTime());
    }

    /** "miércoles 14/10" (para avisos y mensajes). */
    public static String textoFecha(LocalDate fecha) {
        return FECHA_TEXTO.format(fecha);
    }

    /** "18:00". */
    public static String textoHora(LocalTime hora) {
        return HORA.format(hora);
    }

    /** "miércoles" (en minúscula, como va en una oración). */
    public static String nombreDia(DayOfWeek dia) {
        return dia.getDisplayName(TextStyle.FULL, ES_AR);
    }

    /** "Miércoles" (como se guardan los días de apertura). */
    public static String nombreDiaCapitalizado(DayOfWeek dia) {
        String nombre = nombreDia(dia);
        return Character.toUpperCase(nombre.charAt(0)) + nombre.substring(1);
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
