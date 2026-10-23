package com.LosCiruelos.padel_club_api.Exceptions;

import org.springframework.http.HttpStatus;

/** Se superó un tope de uso (por ejemplo, códigos por WhatsApp por día): 429. */
public class LimiteExcedidoException extends ReservaException {
    public LimiteExcedidoException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
