package com.LosCiruelos.padel_club_api.Services.WhatsApp;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Elige el proveedor de WhatsApp según {@code whatsapp.provider} (igual que EmailServiceFactory). */
@Component
@RequiredArgsConstructor
public class WhatsAppServiceFactory {

    @Value("${whatsapp.provider:meta}") // "meta"
    private String provider;

    private final Map<String, WhatsAppService> services;

    public WhatsAppService getService() {
        WhatsAppService service = services.get(provider);
        if (service == null) {
            throw new IllegalStateException("WhatsApp provider no soportado: " + provider);
        }
        return service;
    }
}
