package com.LosCiruelos.padel_club_api.usuarios.exception;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

/** Se superó un tope de uso (por ejemplo, códigos por WhatsApp por día): 429. */
public class LimiteExcedidoException extends ReservaException {
    public LimiteExcedidoException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
