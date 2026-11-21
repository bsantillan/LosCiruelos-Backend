package com.LosCiruelos.padel_club_api.pagos.entity.enums;

import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

/**
 * Los medios de la caja: efectivo, lo que entra a la cuenta de Mercado Pago (online, QR de la caja,
 * transferencias y tarjetas en su posnet), Cuenta DNI y las tarjetas de otro posnet. También son los
 * grupos en que el panel muestra los medios de cobro.
 */
public enum MedioDeCaja {
    EFECTIVO,
    MERCADO_PAGO,
    CUENTA_DNI,
    TARJETA;

    /** El medio de un cobro de una reserva (una bonificación no es plata: null). */
    public static MedioDeCaja de(PaymentProvider provider) {
        return switch (provider) {
            case MANUAL -> EFECTIVO;
            case MERCADO_PAGO, TRANSFERENCIA_MP, TARJETA_MP -> MERCADO_PAGO;
            case CUENTA_DNI -> CUENTA_DNI;
            case TARJETA -> TARJETA;
            case BONIFICACION -> null;
        };
    }

    /** El medio de una venta suelta (y el grupo de un medio de cobro en el panel). */
    public static MedioDeCaja de(MedioDePago medio) {
        return switch (medio) {
            case EFECTIVO -> EFECTIVO;
            case MERCADO_PAGO, TRANSFERENCIA_MP, TARJETA_MP -> MERCADO_PAGO;
            case CUENTA_DNI -> CUENTA_DNI;
            case TARJETA -> TARJETA;
        };
    }
}
