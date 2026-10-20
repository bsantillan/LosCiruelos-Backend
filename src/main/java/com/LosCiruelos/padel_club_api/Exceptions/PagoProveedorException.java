package com.LosCiruelos.padel_club_api.Exceptions;

import org.springframework.http.HttpStatus;

/** Falla al comunicarse con el proveedor de pagos (Mercado Pago). */
public class PagoProveedorException extends ReservaException {
    public PagoProveedorException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}
