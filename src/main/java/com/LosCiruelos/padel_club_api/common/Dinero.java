package com.LosCiruelos.padel_club_api.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.stream.Stream;

/** Montos en pesos: siempre BigDecimal, con 2 decimales al mostrarlos o devolverlos. */
public final class Dinero {

    private static final Locale ES_AR = Locale.of("es", "AR");

    private Dinero() {
    }

    /** Redondeado a centavos (mitad para arriba). */
    public static BigDecimal redondear(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal sumar(Stream<BigDecimal> montos) {
        return montos.reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Para textos de avisos: $7.500 · $7.500,5 · $0 si es null. */
    public static String texto(BigDecimal monto) {
        if (monto == null) {
            return "$0";
        }
        DecimalFormat formato = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(ES_AR));
        return "$" + formato.format(monto);
    }
}
