package com.LosCiruelos.padel_club_api.club.service;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.club.dto.response.BajaDeCanchaResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.CancelacionesResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.club.exception.HorariosConReservasException;

import lombok.RequiredArgsConstructor;

/**
 * La baja de una cancha, como un cambio de horario que deja reservas afuera: si hay reservas de hoy en
 * adelante o turnos fijos activos, sin {@code confirmar} responde 409 con la lista; con
 * {@code confirmar}, la desactiva (en su transacción) y después da de baja cada turno fijo y cancela
 * cada reserva con reembolso total y aviso, cada uno en su transacción.
 */
@Service
@RequiredArgsConstructor
public class BajaDeCanchas {

    private final CanchaAdminService canchaAdminService;
    private final HorariosOcupados horariosOcupados;

    public BajaDeCanchaResponse darDeBaja(Long id, boolean confirmar, String emailAdmin) {
        FueraDeHorarioResponse ocupado = horariosOcupados.deLaCancha(id);
        boolean hayAlgo = !ocupado.reservasFueraDeHorario().isEmpty() || !ocupado.turnosFijosFueraDeHorario().isEmpty();
        if (hayAlgo && !confirmar) {
            throw HorariosConReservasException.porBajaDeCancha(ocupado);
        }
        var cancha = canchaAdminService.desactivar(id);
        if (!hayAlgo) {
            return new BajaDeCanchaResponse(cancha, CancelacionesResponse.NINGUNA, java.util.List.of());
        }
        HorariosOcupados.LiberacionDeCancha liberacion = horariosOcupados.liberarCancha(id, emailAdmin);
        return new BajaDeCanchaResponse(cancha, liberacion.cancelaciones(), liberacion.turnosFijosDadosDeBaja());
    }
}
