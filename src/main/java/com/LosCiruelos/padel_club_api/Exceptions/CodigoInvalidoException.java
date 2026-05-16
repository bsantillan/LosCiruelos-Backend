package com.LosCiruelos.padel_club_api.Exceptions;

public class CodigoInvalidoException extends RuntimeException {
    public CodigoInvalidoException(String mensaje) {
        super(mensaje);
    }
}
