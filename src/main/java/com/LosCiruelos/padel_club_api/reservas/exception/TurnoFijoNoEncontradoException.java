package com.LosCiruelos.padel_club_api.reservas.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

public class TurnoFijoNoEncontradoException extends ReservaException {
    public TurnoFijoNoEncontradoException(Long id) {
        super("Turno fijo no encontrado: " + id, HttpStatus.NOT_FOUND);
    }
}
