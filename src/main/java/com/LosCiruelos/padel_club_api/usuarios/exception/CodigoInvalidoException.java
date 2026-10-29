package com.LosCiruelos.padel_club_api.usuarios.exception;

public class CodigoInvalidoException extends RuntimeException {
    public CodigoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
