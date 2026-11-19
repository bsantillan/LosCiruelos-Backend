package com.LosCiruelos.padel_club_api.pagos.entity.enums;

import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

/**
 * Los medios de la caja: efectivo, lo que entra a la cuenta de Mercado Pago (online, QR de la caja y
 * transferencias) y Cuenta DNI.
 */
public enum MedioDeCaja {
    EFECTIVO,
    MERCADO_PAGO,
    CUENTA_DNI;

    /** El medio de un cobro de una reserva (una bonificación no es plata: null). */
    public static MedioDeCaja de(PaymentProvider provider) {
        return switch (provider) {
            case MANUAL -> EFECTIVO;
            case MERCADO_PAGO, TRANSFERENCIA_MP -> MERCADO_PAGO;
            case CUENTA_DNI -> CUENTA_DNI;
            case BONIFICACION -> null;
        };
    }

    /** El medio de una venta suelta. */
    public static MedioDeCaja de(MedioDePago medio) {
        return switch (medio) {
            case EFECTIVO -> EFECTIVO;
            case MERCADO_PAGO, TRANSFERENCIA_MP -> MERCADO_PAGO;
            case CUENTA_DNI -> CUENTA_DNI;
        };
    }
}
