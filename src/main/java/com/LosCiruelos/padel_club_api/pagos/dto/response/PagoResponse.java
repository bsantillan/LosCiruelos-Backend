package com.LosCiruelos.padel_club_api.pagos.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.CanalDePago;

import lombok.Data;

@Data
public class PagoResponse {
    Long id;
    Long reservaId;
    BigDecimal monto;
    EstadoPago estado;
    PaymentProvider provider;
    // por dónde entró: EFECTIVO, MERCADO_PAGO_ONLINE, MERCADO_PAGO_QR, TRANSFERENCIA_MP, CUENTA_DNI o BONIFICACION
    CanalDePago canal;
    // id de la preferencia de Mercado Pago (para inicializar el Brick / Wallet)
    String paymentToken;
    // id del pago en Mercado Pago
    String externalPaymentId;
    LocalDateTime createdAt;
    LocalDateTime paidAt;
    // cuánto se devolvió (DEVUELTO) o hay que devolver (A_REEMBOLSAR)
    BigDecimal montoReembolsado;
    // qué cubre el pago (cancha y líneas de producto); en un QR esperando el pago, lo que cubre al cobrarse
    java.util.List<ImputacionResponse> imputaciones;
    // motivo de una bonificación (provider BONIFICACION)
    String motivo;
    // pago con Cuenta DNI o por transferencia: si el ADMIN comprobó que entró a la cuenta (null en los otros) y cuándo
    Boolean verificado;
    LocalDateTime verificadoAt;
    // estado de la reserva después de la operación
    EstadoReserva estadoReserva;
    // detalle del proveedor al cobrar (ej. motivo del rechazo); solo en la respuesta del cobro
    String detalle;
}
