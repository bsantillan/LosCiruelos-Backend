package com.LosCiruelos.padel_club_api.Exceptions;

import org.springframework.http.HttpStatus;

public class ProductoNoEncontradoException extends ReservaException {
    public ProductoNoEncontradoException(Long id) {
        super("Producto no encontrado: " + id, HttpStatus.NOT_FOUND);
    }
}
