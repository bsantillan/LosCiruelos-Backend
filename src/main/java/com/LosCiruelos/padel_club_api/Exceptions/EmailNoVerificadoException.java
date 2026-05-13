package com.LosCiruelos.padel_club_api.Exceptions;

public class EmailNoVerificadoException extends RuntimeException {
    public EmailNoVerificadoException(String mensaje) {
        super(mensaje);
    }
}
