package com.LosCiruelos.padel_club_api.Exceptions;

import org.springframework.http.HttpStatus;

/** No hay unidades suficientes de un producto (o no hay suficientes libres en ese horario). */
public class StockInsuficienteException extends ReservaException {
    public StockInsuficienteException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
