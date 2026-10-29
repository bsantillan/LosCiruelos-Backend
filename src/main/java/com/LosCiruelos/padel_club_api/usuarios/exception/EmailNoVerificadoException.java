package com.LosCiruelos.padel_club_api.usuarios.exception;

public class EmailNoVerificadoException extends RuntimeException {
    public EmailNoVerificadoException(String mensaje) {
        super(mensaje);
    }
}
