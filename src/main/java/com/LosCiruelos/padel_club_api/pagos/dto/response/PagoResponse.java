package com.LosCiruelos.padel_club_api.pagos.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;

import lombok.Data;

@Data
public class PagoResponse {
    Long id;
    Long reservaId;
    // partido: qué jugador pagó (o a quién se le bonificó)
    Long jugadorId;
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
}
