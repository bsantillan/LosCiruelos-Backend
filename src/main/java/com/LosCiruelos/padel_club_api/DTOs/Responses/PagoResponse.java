package com.LosCiruelos.padel_club_api.DTOs.Responses;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.Entities.Pago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.PaymentProvider;

import lombok.Data;

@Data
public class PagoResponse {
    Long id;
    Long reservaId;
    BigDecimal monto;
    EstadoPago estado;
    PaymentProvider provider;
    // id de la preferencia de Mercado Pago (para inicializar el Brick / Wallet)
    String paymentToken;
    // id del pago en Mercado Pago
    String externalPaymentId;
    LocalDateTime createdAt;
    LocalDateTime paidAt;
    // cuánto se devolvió (DEVUELTO) o hay que devolver (A_REEMBOLSAR)
    BigDecimal montoReembolsado;
    // motivo de una bonificación (provider BONIFICACION)
    String motivo;
    // estado de la reserva después de la operación
    EstadoReserva estadoReserva;
    // detalle del proveedor al cobrar (ej. motivo del rechazo); solo en la respuesta del cobro
    String detalle;

    public static PagoResponse from(Pago pago) {
        PagoResponse response = new PagoResponse();
        response.setId(pago.getId());
        response.setReservaId(pago.getReserva().getId());
        response.setMonto(pago.getMonto());
        response.setEstado(pago.getEstado());
        response.setProvider(pago.getProvider());
        response.setPaymentToken(pago.getPaymentToken());
        response.setExternalPaymentId(pago.getExternalPaymentId());
        response.setCreatedAt(pago.getCreatedAt());
        response.setPaidAt(pago.getPaidAt());
        response.setMontoReembolsado(pago.getMontoReembolsado());
        response.setMotivo(pago.getMotivo());
        response.setEstadoReserva(pago.getReserva().getEstado());
        return response;
    }
}
