package com.LosCiruelos.padel_club_api.reservas.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

/** La reserva no existe o no pertenece al usuario (se responde igual para no revelar ids ajenos). */
public class ReservaNoEncontradaException extends ReservaException {
    public ReservaNoEncontradaException(Long id) {
        super("Reserva no encontrada: " + id, HttpStatus.NOT_FOUND);
    }
}
