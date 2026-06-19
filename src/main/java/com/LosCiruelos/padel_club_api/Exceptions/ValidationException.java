package com.LosCiruelos.padel_club_api.Exceptions;

import java.util.Map;

import lombok.Getter;

@Getter
public class ValidationException extends RuntimeException {
    private final Map<String, String> errores;

    public ValidationException(Map<String, String> errores) {
        super("Errores de validación");
        this.errores = errores;
    }
}
