package com.LosCiruelos.padel_club_api.common.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

import lombok.Getter;

/**
 * Error de negocio de reservas o pagos. Por defecto es un 400 (datos inválidos);
 * las subclases indican conflictos de estado, recursos inexistentes o fallas del proveedor.
 */
@Getter
public class ReservaException extends RuntimeException {

    private final HttpStatus status;

    // datos que el front necesita además del mensaje (van en el body del error, junto a "error")
    private final Map<String, Object> datos;

    public ReservaException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    protected ReservaException(String message, HttpStatus status) {
        this(message, status, Map.of());
    }

    protected ReservaException(String message, HttpStatus status, Map<String, Object> datos) {
        super(message);
        this.status = status;
        this.datos = datos;
    }
}
