package com.LosCiruelos.padel_club_api.Exceptions;

import org.springframework.http.HttpStatus;

/** La reserva no existe o no pertenece al usuario (se responde igual para no revelar ids ajenos). */
public class ReservaNoEncontradaException extends ReservaException {
    public ReservaNoEncontradaException(Long id) {
        super("Reserva no encontrada: " + id, HttpStatus.NOT_FOUND);
    }
}
