package com.LosCiruelos.padel_club_api.pagos.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

public class DevolucionNoEncontradaException extends ReservaException {
    public DevolucionNoEncontradaException(Long id) {
        super("Devolución no encontrada: " + id, HttpStatus.NOT_FOUND);
    }
}
