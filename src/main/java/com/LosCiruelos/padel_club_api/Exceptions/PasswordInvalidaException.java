package com.LosCiruelos.padel_club_api.Exceptions;

public class PasswordInvalidaException extends RuntimeException {

    public PasswordInvalidaException() {
        super("La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un símbolo");
    }
}
