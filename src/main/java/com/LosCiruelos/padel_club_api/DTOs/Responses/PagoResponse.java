package com.LosCiruelos.padel_club_api.DTOs.Responses;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.PaymentProvider;
import com.LosCiruelos.padel_club_api.Entities.Pago;

import lombok.Data;

@Data
public class PagoResponse {
    Long id;
    Long reservaId;
    BigDecimal monto;
    EstadoPago estado;
    PaymentProvider provider;
    String initPoint; 
    String preferenceId;
    LocalDateTime createdAt;
    LocalDateTime paidAt;

    public static PagoResponse from(Pago pago, String initPoint) {
        PagoResponse response = new PagoResponse();
        response.setId(pago.getId());
        response.setReservaId(pago.getReserva().getId());
        response.setMonto(pago.getMonto());
        response.setEstado(pago.getEstado());
        response.setProvider(pago.getProvider());
        response.setInitPoint(initPoint);
        response.setPreferenceId(pago.getPreferenceId());
        response.setCreatedAt(pago.getCreatedAt());
        response.setPaidAt(pago.getPaidAt());
        return response;
    }

    public static PagoResponse from(Pago pago) {
        return from(pago, null);
    }
}