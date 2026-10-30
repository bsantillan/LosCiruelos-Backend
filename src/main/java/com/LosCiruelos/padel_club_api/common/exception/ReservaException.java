package com.LosCiruelos.padel_club_api.common.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Error de negocio de reservas o pagos. Por defecto es un 400 (datos inválidos);
 * las subclases indican conflictos de estado, recursos inexistentes o fallas del proveedor.
 */
@Getter
public class ReservaException extends RuntimeException {

    private final HttpStatus status;

    public ReservaException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    protected ReservaException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }
}
