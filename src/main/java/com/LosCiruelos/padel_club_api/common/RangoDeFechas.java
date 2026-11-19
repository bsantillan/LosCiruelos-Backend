package com.LosCiruelos.padel_club_api.common;

import java.time.LocalDate;

/**
 * La primera y la última fecha de una lista entera (sin el filtro de fechas ni la paginación), para
 * que el front muestre de qué fecha a qué fecha va "Todo el período". Los dos null si no hay nada.
 */
public record RangoDeFechas(LocalDate desde, LocalDate hasta) {

    public static final RangoDeFechas VACIO = new RangoDeFechas(null, null);
}
