package com.LosCiruelos.padel_club_api.security;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.Exceptions.CredencialesInvalidasException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    public GoogleTokenVerifier(@Value("${google.client-id}") String clientId) {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(clientId))
                .build();
    }

    public GoogleUser verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();

                String firstName = (String) payload.get("given_name");
                String lastName = (String) payload.get("family_name");

                if (lastName == null && firstName != null && firstName.contains(" ")) {
                    String[] parts = firstName.trim().split(" ", 2);
                    firstName = parts[0];
                    lastName = parts[1];
                }

                // Solo se vincula por email si Google confirma que el email es de esa persona
                if (!Boolean.TRUE.equals(payload.getEmailVerified())) {
                    throw new CredencialesInvalidasException("El email de la cuenta de Google no está verificado");
                }

                return new GoogleUser(payload.getEmail(), firstName, lastName);
            }
            log.warn("Google rechazó el token - idToken es null");
            throw new CredencialesInvalidasException("Token de Google inválido");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Excepción verificando token de Google: {}", e.getMessage(), e);
            throw new CredencialesInvalidasException("No se pudo verificar el token de Google");
        }
    }
}
