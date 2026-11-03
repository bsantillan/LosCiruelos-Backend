package com.LosCiruelos.padel_club_api.ventas.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

public class VentaNoEncontradaException extends ReservaException {
    public VentaNoEncontradaException(Long id) {
        super("Venta no encontrada: " + id, HttpStatus.NOT_FOUND);
    }
}
