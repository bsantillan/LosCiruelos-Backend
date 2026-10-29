package com.LosCiruelos.padel_club_api.club.exception;

public class CanchaNotFoundException extends RuntimeException {
    public CanchaNotFoundException(String message) {
        super(message);
    }
}