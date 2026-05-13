package com.LosCiruelos.padel_club_api.Exceptions;

public class EmailEnUsoException extends RuntimeException {
    public EmailEnUsoException() {
        super("El email ya está registrado");
    }
}
