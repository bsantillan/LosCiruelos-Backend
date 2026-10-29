package com.LosCiruelos.padel_club_api.pagos.exception;

import lombok.Getter;

/**
 * Mercado Pago no pudo hacer un reembolso. No llega al cliente: el pago queda
 * REEMBOLSO_PENDIENTE y un job lo reintenta.
 */
@Getter
public class ReembolsoFallidoException extends PagoProveedorException {

    // true si reintentar no tiene sentido (ej. el pago no existe en MP)
    private final boolean definitivo;

    public ReembolsoFallidoException(boolean definitivo) {
        super("Mercado Pago no pudo hacer el reembolso.");
        this.definitivo = definitivo;
    }
}
