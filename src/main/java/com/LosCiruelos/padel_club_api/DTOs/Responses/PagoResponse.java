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
    String paymentToken;
    LocalDateTime createdAt;
    LocalDateTime paidAt;

    public static PagoResponse from(Pago pago) {
        PagoResponse response = new PagoResponse();
        response.setId(pago.getId());
        response.setReservaId(pago.getReserva().getId());
        response.setMonto(pago.getMonto());
        response.setEstado(pago.getEstado());
        response.setProvider(pago.getProvider());
        response.setPaymentToken(pago.getPaymentToken());
        response.setCreatedAt(pago.getCreatedAt());
        response.setPaidAt(pago.getPaidAt());
        return response;
    }
}