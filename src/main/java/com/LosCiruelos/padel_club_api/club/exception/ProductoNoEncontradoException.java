package com.LosCiruelos.padel_club_api.club.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

public class ProductoNoEncontradoException extends ReservaException {
    public ProductoNoEncontradoException(Long id) {
        super("Producto no encontrado: " + id, HttpStatus.NOT_FOUND);
    }
}
