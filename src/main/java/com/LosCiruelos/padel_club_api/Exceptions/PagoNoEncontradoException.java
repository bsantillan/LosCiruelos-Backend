package com.LosCiruelos.padel_club_api.Exceptions;

import org.springframework.http.HttpStatus;

public class PagoNoEncontradoException extends ReservaException {
    public PagoNoEncontradoException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
