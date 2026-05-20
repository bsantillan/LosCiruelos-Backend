package com.LosCiruelos.padel_club_api.Services.Pagos;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.google.api.client.util.Value;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentServiceFactory {

    @Value("${payment.provider}")
    private String provider;

    private final Map<String, PaymentService> services;

    public PaymentService getService() {
        PaymentService service = services.get(provider);
        if (service == null) {
            throw new IllegalStateException("Payment provider no soportado: " + provider);
        }
        return service;
    }
}