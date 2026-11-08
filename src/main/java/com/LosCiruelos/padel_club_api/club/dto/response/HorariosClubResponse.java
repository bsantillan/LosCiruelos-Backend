package com.LosCiruelos.padel_club_api.club.dto.response;

import java.util.List;

import java.time.LocalDate;

/**
 * Los días y horarios de apertura del club (público): de lunes a domingo; un día que no está, el
 * club no abre. Con los próximos días cerrados y la duración mínima y máxima de los turnos para
 * armar la grilla.
 */
public record HorariosClubResponse(List<ConfiguracionResponse.Horario> horarios, List<Cierre> cierres,
        int duracionMinimaTurno, int duracionMaximaTurno) {

    public record Cierre(LocalDate fecha, String motivo) {
    }
}
