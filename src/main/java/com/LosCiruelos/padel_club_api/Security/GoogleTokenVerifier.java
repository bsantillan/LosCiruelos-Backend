package com.LosCiruelos.padel_club_api.Security;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.DTOs.Responses.GoogleUser;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;

@Component
public class GoogleTokenVerifier {

    private final GoogleIdTokenVerifier verifier;

    @Value("${google.secret}")
    private String secret;

    public GoogleTokenVerifier() {
        this.verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(secret))
                .build();
    }

    public GoogleUser verify(String idTokenString) {
        try {
            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                return new GoogleUser(
                        payload.getEmail(),
                        (String) payload.get("given_name"),
                        (String) payload.get("family_name")
                );
            } else {
                throw new RuntimeException("Token de Google inválido");
            }
        } catch (Exception e) {
            throw new RuntimeException("Error verificando token de Google", e);
        }
    }
}
