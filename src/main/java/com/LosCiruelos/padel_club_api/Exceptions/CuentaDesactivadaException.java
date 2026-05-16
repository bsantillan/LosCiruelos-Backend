package com.LosCiruelos.padel_club_api.Exceptions;

public class CuentaDesactivadaException extends RuntimeException {
    public CuentaDesactivadaException() {
        super("Cuenta desactivada. Contactá al club para reactivarla.");
    }
}
