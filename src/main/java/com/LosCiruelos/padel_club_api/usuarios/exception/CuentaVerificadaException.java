package com.LosCiruelos.padel_club_api.usuarios.exception;

public class CuentaVerificadaException extends RuntimeException {
    public CuentaVerificadaException() {
        super("Cuenta ya verificada");
    }
}
