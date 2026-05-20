package com.LosCiruelos.padel_club_api.Services.Pagos;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.DTOs.PaymentResult;
import com.LosCiruelos.padel_club_api.Entities.Reserva;

public interface PaymentService {
    String iniciarPago(Reserva reserva, BigDecimal monto);
    PaymentResult consultarPago(String paymentId);
}