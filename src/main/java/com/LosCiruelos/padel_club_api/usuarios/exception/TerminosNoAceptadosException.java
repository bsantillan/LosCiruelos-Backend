package com.LosCiruelos.padel_club_api.usuarios.exception;

public class TerminosNoAceptadosException extends RuntimeException {
    public TerminosNoAceptadosException() {
        super("Para registrarte debes aceptar los términos y condiciones");
    }
}
