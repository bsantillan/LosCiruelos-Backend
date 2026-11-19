package com.LosCiruelos.padel_club_api.pagos.service;

import java.math.BigDecimal;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentServiceFactory;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr;

import lombok.RequiredArgsConstructor;

/**
 * Devuelve plata por la API del proveedor: un pago online con la API de pagos y un QR con la de
 * orders. Lo usan el reembolso de la cancelación y la devolución por línea del ADMIN.
 */
@Component
@RequiredArgsConstructor
class DevolucionesPorApi {

    private final PaymentServiceFactory paymentServiceFactory;
    private final ProveedorCobroQr proveedorQr;

    /**
     * @param operacion null para el reembolso de la cancelación (la clave de siempre); si no, distingue
     *                  esta devolución en la clave de idempotencia
     */
    void devolver(Pago pago, BigDecimal monto, String operacion) {
        if (pago.esCobroQr()) {
            proveedorQr.reembolsar(operacion == null ? pago.ordenQr() : pago.ordenQr(operacion), monto);
        } else if (operacion == null) {
            paymentServiceFactory.getService().reembolsar(pago, monto);
        } else {
            paymentServiceFactory.getService().reembolsar(pago, monto, operacion);
        }
    }
}
