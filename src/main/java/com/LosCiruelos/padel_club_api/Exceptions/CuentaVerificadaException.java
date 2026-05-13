package com.LosCiruelos.padel_club_api.Exceptions;

public class CuentaVerificadaException extends RuntimeException {
    public CuentaVerificadaException() {
        super("Cuenta ya verificada");
    }
}
