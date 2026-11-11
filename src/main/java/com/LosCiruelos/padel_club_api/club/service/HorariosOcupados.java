package com.LosCiruelos.padel_club_api.club.service;

import java.time.LocalDate;
import java.util.List;

import com.LosCiruelos.padel_club_api.club.dto.response.CancelacionesResponse;
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

    /**
     * Lo que hay el día {@code fecha}: las reservas que todavía no empezaron y no están canceladas
     * ni vencidas, y los turnos fijos activos que se juegan ese día.
     */
    FueraDeHorarioResponse delDia(LocalDate fecha);

    /**
     * Cancela esas reservas como lo haría el ADMIN, con reembolso total y aviso al cliente, cada una
     * en su transacción. Las que no se pueden cancelar quedan informadas, con el motivo.
     */
    CancelacionesResponse cancelar(List<Long> reservaIds, String emailAdmin);
}
