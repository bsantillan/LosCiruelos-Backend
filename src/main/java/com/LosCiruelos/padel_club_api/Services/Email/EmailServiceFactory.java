package com.LosCiruelos.padel_club_api.Services.Email;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class EmailServiceFactory {

    @Value("${email.provider}")  // "gmail" o "sendgrid"
    private String provider;

    private final Map<String, EmailService> services;

    public EmailService getService() {
        EmailService service = services.get(provider);
        if (service == null) {
            throw new IllegalStateException("Email provider no soportado: " + provider);
        }
        return service;
    }
}
