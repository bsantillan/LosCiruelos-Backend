package com.LosCiruelos.padel_club_api.Security;

import java.util.regex.Pattern;

public class PasswordValidator {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

    public static boolean esValida(String password) {
        return password != null && PASSWORD_PATTERN.matcher(password).matches();
    }
}
