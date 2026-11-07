package com.LosCiruelos.padel_club_api.ventas.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

/** Mercado Pago no devolvió la plata de una venta cobrada con QR: no se registra nada y se puede reintentar. */
public class ReembolsoQrException extends ReservaException {
    public ReembolsoQrException() {
        super("Mercado Pago no aceptó el reembolso. Intentá de nuevo.", HttpStatus.BAD_GATEWAY);
    }
}
