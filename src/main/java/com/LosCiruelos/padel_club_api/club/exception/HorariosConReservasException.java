package com.LosCiruelos.padel_club_api.club.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

import lombok.Getter;

/** El horario nuevo deja afuera reservas o turnos fijos y no se confirmó: 409 con la lista, sin guardar. */
@Getter
public class HorariosConReservasException extends ReservaException {

    private final FueraDeHorarioResponse fuera;

    public HorariosConReservasException(FueraDeHorarioResponse fuera) {
        super(mensaje(fuera.reservasFueraDeHorario().size(), fuera.turnosFijosFueraDeHorario().size()),
                HttpStatus.CONFLICT);
        this.fuera = fuera;
    }

    private static String mensaje(int reservas, int turnosFijos) {
        String que = reservas > 0 && turnosFijos > 0
                ? cantidad(reservas, "reserva", "reservas") + " y " + cantidad(turnosFijos, "turno fijo", "turnos fijos")
                : reservas > 0 ? cantidad(reservas, "reserva", "reservas")
                        : cantidad(turnosFijos, "turno fijo", "turnos fijos");
        return "El horario nuevo deja afuera " + que + ". Para guardarlo igual, mandá \"confirmar\": true: no se "
                + "cancela ni se modifica nada (se reprograman o cancelan a mano).";
    }

    private static String cantidad(int n, String singular, String plural) {
        return n == 1 ? "1 " + singular : n + " " + plural;
    }
}
