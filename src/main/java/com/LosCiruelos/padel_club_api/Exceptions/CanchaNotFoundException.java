package com.LosCiruelos.padel_club_api.Exceptions;

public class CanchaNotFoundException extends RuntimeException {
    public CanchaNotFoundException(String message) {
        super(message);
    }
}