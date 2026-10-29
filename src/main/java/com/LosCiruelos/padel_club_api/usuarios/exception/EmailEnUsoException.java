package com.LosCiruelos.padel_club_api.usuarios.exception;

public class EmailEnUsoException extends RuntimeException {
    public EmailEnUsoException() {
        super("El email ya está registrado");
    }
}
