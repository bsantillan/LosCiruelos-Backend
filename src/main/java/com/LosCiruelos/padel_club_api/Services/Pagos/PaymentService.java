package com.LosCiruelos.padel_club_api.Services.Pagos;

import java.math.BigDecimal;
import java.util.Map;

import com.LosCiruelos.padel_club_api.DTOs.PaymentResult;
import com.LosCiruelos.padel_club_api.Entities.Pago;

public interface PaymentService {

    /** Crea la preferencia de pago por el monto de {@code pago} y devuelve su id. */
    String crearPreferencia(Pago pago);

    /**
     * Cobra {@code pago} con los datos de tarjeta del Brick. El monto siempre sale de
     * {@code pago.getMonto()}, nunca de {@code formData}.
     */
    PaymentResult cobrar(Pago pago, Map<String, Object> formData);

    /** Consulta el estado actual de un pago en el proveedor. */
    PaymentResult consultarPago(String paymentId);

    /** Devuelve {@code monto} (todo o parte) de un pago cobrado online. */
    void reembolsar(Pago pago, BigDecimal monto);
}
