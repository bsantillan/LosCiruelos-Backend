package com.LosCiruelos.padel_club_api.pagos.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

public class PagoNoEncontradoException extends ReservaException {
    public PagoNoEncontradoException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
