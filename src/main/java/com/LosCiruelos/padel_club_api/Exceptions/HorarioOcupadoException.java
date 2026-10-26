package com.LosCiruelos.padel_club_api.Exceptions;

import org.springframework.http.HttpStatus;

public class HorarioOcupadoException extends ReservaException {
    public HorarioOcupadoException() {
        super("El horario solicitado se superpone con un turno ya reservado en esa cancha.", HttpStatus.CONFLICT);
    }

    public HorarioOcupadoException(String mensaje) {
        super(mensaje, HttpStatus.CONFLICT);
    }
}
