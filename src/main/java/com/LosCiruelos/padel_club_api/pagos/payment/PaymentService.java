package com.LosCiruelos.padel_club_api.pagos.payment;

import java.math.BigDecimal;
import java.util.Map;

import com.LosCiruelos.padel_club_api.pagos.entity.Pago;

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

    /** Devuelve {@code monto} (todo o parte) de un pago cobrado online (el reembolso de la cancelación). */
    default void reembolsar(Pago pago, BigDecimal monto) {
        reembolsar(pago, monto, "reembolso");
    }

    /**
     * Devuelve {@code monto} de un pago cobrado online. {@code operacion} distingue cada devolución en
     * su clave de idempotencia (el mismo pago se puede devolver en partes).
     */
    void reembolsar(Pago pago, BigDecimal monto, String operacion);
}
