package com.LosCiruelos.padel_club_api.club.exception;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.common.Fechas;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

import lombok.Getter;

/** El horario nuevo deja afuera reservas o turnos fijos y no se confirmó: 409 con la lista, sin guardar. */
@Getter
public class HorariosConReservasException extends ReservaException {

    private final FueraDeHorarioResponse fuera;

    private HorariosConReservasException(String mensaje, FueraDeHorarioResponse fuera) {
        super(mensaje, HttpStatus.CONFLICT);
        this.fuera = fuera;
    }

    /** El horario nuevo de PUT /admin/configuracion deja afuera reservas o turnos fijos. */
    public static HorariosConReservasException porHorario(FueraDeHorarioResponse fuera) {
        int reservas = fuera.reservasFueraDeHorario().size();
        int turnosFijos = fuera.turnosFijosFueraDeHorario().size();
        String que = reservas > 0 && turnosFijos > 0
                ? cantidad(reservas, "reserva", "reservas") + " y " + cantidad(turnosFijos, "turno fijo", "turnos fijos")
                : reservas > 0 ? cantidad(reservas, "reserva", "reservas")
                        : cantidad(turnosFijos, "turno fijo", "turnos fijos");
        return new HorariosConReservasException("El horario nuevo deja afuera " + que + ". Para guardarlo igual, mandá "
                + "\"confirmar\": true: las reservas se cancelan con reembolso total y se avisa a cada cliente, y los "
                + "turnos fijos no crean partidos nuevos en ese horario.", fuera);
    }

    /** El día que se quiere cerrar tiene reservas que todavía no empezaron. */
    public static HorariosConReservasException porCierre(LocalDate fecha, FueraDeHorarioResponse delDia) {
        return new HorariosConReservasException("El " + Fechas.textoFecha(fecha) + " hay "
                + cantidad(delDia.reservasFueraDeHorario().size(), "reserva", "reservas") + ". Para cerrar igual, "
                + "mandá \"confirmar\": true: se cancelan con reembolso total y se avisa a cada cliente.", delDia);
    }

    private static String cantidad(int n, String singular, String plural) {
        return n == 1 ? "1 " + singular : n + " " + plural;
    }
}
