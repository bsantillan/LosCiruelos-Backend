package com.LosCiruelos.padel_club_api.Exceptions;

import org.springframework.http.HttpStatus;

/** El rol no alcanza para lo que se pidió, con un mensaje específico: 403. */
public class PermisoDenegadoException extends ReservaException {
    public PermisoDenegadoException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
