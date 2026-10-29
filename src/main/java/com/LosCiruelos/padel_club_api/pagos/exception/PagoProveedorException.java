package com.LosCiruelos.padel_club_api.pagos.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

/** Falla al comunicarse con el proveedor de pagos (Mercado Pago). */
public class PagoProveedorException extends ReservaException {
    public PagoProveedorException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}
