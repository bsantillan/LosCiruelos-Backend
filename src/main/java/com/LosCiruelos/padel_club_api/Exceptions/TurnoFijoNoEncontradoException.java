package com.LosCiruelos.padel_club_api.Exceptions;

import org.springframework.http.HttpStatus;

public class TurnoFijoNoEncontradoException extends ReservaException {
    public TurnoFijoNoEncontradoException(Long id) {
        super("Turno fijo no encontrado: " + id, HttpStatus.NOT_FOUND);
    }
}
