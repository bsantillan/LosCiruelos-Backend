package com.LosCiruelos.padel_club_api.pagos.payment;

import java.math.BigDecimal;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/** Resultado de un pago normalizado, independiente del proveedor. */
@Data
@Builder
@AllArgsConstructor
public class PaymentResult {
    private EstadoPayment estado;
    // id de la reserva (external_reference)
    private String externalReference;
    // id de nuestro Pago (metadata pago_id)
    private Long pagoId;
    // monto efectivamente cobrado por el proveedor
    private BigDecimal monto;
    // cuánto se devolvió en el proveedor (sumando reembolsos parciales)
    private BigDecimal montoDevuelto;
    // id del pago en el proveedor
    private String paymentId;
    // detalle del estado (ej. "cc_rejected_insufficient_amount")
    private String detalle;
}
