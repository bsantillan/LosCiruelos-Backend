package com.LosCiruelos.padel_club_api.club.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

public class NovedadNoEncontradaException extends ReservaException {
    public NovedadNoEncontradaException(Long id) {
        super("Novedad no encontrada: " + id, HttpStatus.NOT_FOUND);
    }
}
