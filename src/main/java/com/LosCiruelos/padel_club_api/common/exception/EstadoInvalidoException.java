package com.LosCiruelos.padel_club_api.common.exception;

import org.springframework.http.HttpStatus;

/** La operación no se puede hacer en el estado actual de la reserva o del pago. */
public class EstadoInvalidoException extends ReservaException {
    public EstadoInvalidoException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
