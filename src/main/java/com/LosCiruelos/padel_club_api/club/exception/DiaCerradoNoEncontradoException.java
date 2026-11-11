package com.LosCiruelos.padel_club_api.club.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

/** Se quiere reabrir un día que no está cerrado. */
public class DiaCerradoNoEncontradoException extends ReservaException {
    public DiaCerradoNoEncontradoException(String mensaje) {
        super(mensaje, HttpStatus.NOT_FOUND);
    }
}
