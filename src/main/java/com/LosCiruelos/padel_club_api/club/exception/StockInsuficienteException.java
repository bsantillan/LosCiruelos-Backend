package com.LosCiruelos.padel_club_api.club.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

/** No hay unidades suficientes de un producto (o no hay suficientes libres en ese horario). */
public class StockInsuficienteException extends ReservaException {
    public StockInsuficienteException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
