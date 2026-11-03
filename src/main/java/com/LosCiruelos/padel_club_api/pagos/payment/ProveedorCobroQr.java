package com.LosCiruelos.padel_club_api.pagos.payment;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.pagos.entity.Pago;

/**
 * Cobro en el club con el QR de la caja (impreso en el mostrador): se le asigna a la caja una
 * order por el monto exacto y el cliente la paga al escanear el QR. Hoy lo implementa Mercado Pago
 * (API de Orders, modo estático); si Cuenta DNI habilita una API, sería otra implementación.
 */
public interface ProveedorCobroQr {

    /** Si está configurado (token y caja). Sin configurar, el cobro con QR no se ofrece. */
    boolean estaConfigurado();

    /** Asigna a la caja una order por {@code pago.getMonto()} que vence a los {@code minutos} minutos; devuelve su id. */
    String crear(Pago pago, String descripcion, int minutos);

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
