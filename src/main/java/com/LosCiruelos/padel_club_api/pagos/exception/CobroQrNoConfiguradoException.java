package com.LosCiruelos.padel_club_api.pagos.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

/** Falta el token o la caja de Mercado Pago: el cobro con QR no está disponible. */
public class CobroQrNoConfiguradoException extends ReservaException {
    public CobroQrNoConfiguradoException() {
        super("El cobro con QR no está configurado.", HttpStatus.SERVICE_UNAVAILABLE);
    }
}
