package com.LosCiruelos.padel_club_api.pagos.payment;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.pagos.entity.Pago;

/**
 * Cobro en el club con un QR dinámico por un monto exacto. Hoy lo implementa Mercado Pago (API de
 * Orders); si Cuenta DNI habilita una API, sería otra implementación.
 */
public interface ProveedorCobroQr {

    /** Si está configurado (token y caja). Sin configurar, el cobro con QR no se ofrece. */
    boolean estaConfigurado();

    /** Crea la order por {@code pago.getMonto()} que vence a los {@code minutos} minutos. */
    OrdenQr crear(Pago pago, String descripcion, int minutos);

    /** Estado actual de la order en el proveedor. Nunca se confía en lo que trae un webhook. */
    EstadoOrdenQr consultar(String orderId);

    /**
     * Cancela la order (solo mientras no se pagó).
     *
     * @throws OrdenNoCancelableException si el proveedor dice que ya no se puede cancelar (por ejemplo,
     *         porque se pagó o el pago está en curso)
     */
    void cancelar(Pago pago);

    /** Devuelve {@code monto} (todo o parte) de un cobro con QR. */
    void reembolsar(Pago pago, BigDecimal monto);

    /** La order creada: su id y la trama del QR (EMVCo) que el front dibuja. */
    record OrdenQr(String orderId, String qrData) {
    }

    /**
     * @param montoPagado lo que se cobró (null si todavía no se pagó o el proveedor no lo informa)
     * @param pagoId      id del pago dentro de la order (para los reembolsos parciales)
     */
    record EstadoOrdenQr(EstadoOrden estado, BigDecimal montoPagado, String pagoId) {
    }

    enum EstadoOrden {
        CREADA,     // esperando el pago
        PROCESADA,  // pagada y acreditada
        CANCELADA,
        VENCIDA,
        DEVUELTA    // reembolsada entera
    }

    class OrdenNoCancelableException extends RuntimeException {
        public OrdenNoCancelableException(String mensaje) {
            super(mensaje);
        }
    }
}
