package com.LosCiruelos.padel_club_api.pagos.entity.enums;

/** Por dónde entró un pago (distingue los de Mercado Pago: online, QR de la caja o transferencia). */
public enum CanalDePago {
    EFECTIVO,
    MERCADO_PAGO_ONLINE,
    MERCADO_PAGO_QR,
    TRANSFERENCIA_MP,
    CUENTA_DNI,
    BONIFICACION
}
