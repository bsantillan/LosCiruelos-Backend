package com.LosCiruelos.padel_club_api.club.service;

import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;

/**
 * Lo que está reservado y no entra en los horarios de una configuración (todavía sin guardar). Lo
 * implementa el dominio de reservas (que es el que tiene las reservas y los turnos fijos), así club
 * no depende de él.
 */
public interface HorariosOcupados {

    /**
     * Reservas futuras (que todavía no empezaron) que no están canceladas ni vencidas, y turnos fijos
     * activos, cuyo día y horario no entran en los de {@code config}.
     */
    FueraDeHorarioResponse fueraDeHorario(Configuracion config);
}
