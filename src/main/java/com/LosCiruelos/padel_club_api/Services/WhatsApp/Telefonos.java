package com.LosCiruelos.padel_club_api.Services.WhatsApp;

import java.util.Map;

import com.LosCiruelos.padel_club_api.Exceptions.ValidationException;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * Normaliza teléfonos al formato que usa WhatsApp: código de país y número, solo dígitos
 * (ej. {@code 5491123456789}). Los números sin código de país se toman como argentinos y se
 * aceptan con o sin 0 y 15: {@code 011 15-2345-6789}, {@code 11 2345-6789} o
 * {@code +54 9 11 2345 6789} quedan iguales.
 */
public final class Telefonos {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();
    private static final String REGION = "AR";

    private Telefonos() {
    }

    /** @return el número normalizado, o null si viene vacío. Lanza 400 si no es un teléfono válido. */
    public static String normalizar(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return null;
        }
        try {
            PhoneNumber numero = UTIL.parse(telefono, REGION);
            if (!UTIL.isValidNumber(numero)) {
                throw invalido();
            }
            String e164 = UTIL.format(numero, PhoneNumberUtil.PhoneNumberFormat.E164).substring(1);
            // WhatsApp usa los celulares argentinos con 9 después del 54. Sin el 15 no se puede
            // distinguir un celular de un fijo, y un fijo no tiene WhatsApp: se toma como celular.
            if (numero.getCountryCode() == 54 && !e164.startsWith("549")) {
                e164 = "549" + e164.substring(2);
            }
            return e164;
        } catch (NumberParseException e) {
            throw invalido();
        }
    }

    private static ValidationException invalido() {
        return new ValidationException(Map.of("telefono", "Teléfono inválido. Ingresalo con código de área, "
                + "por ejemplo 11 2345-6789."));
    }
}
