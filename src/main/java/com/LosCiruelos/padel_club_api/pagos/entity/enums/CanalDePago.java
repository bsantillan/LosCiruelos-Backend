package com.LosCiruelos.padel_club_api.pagos.entity.enums;

/** Por dónde entró un pago (distingue los de Mercado Pago: online, QR de la caja, transferencia o tarjeta). */
public enum CanalDePago {
    EFECTIVO,
    MERCADO_PAGO_ONLINE,
    MERCADO_PAGO_QR,
    TRANSFERENCIA_MP,
    // tarjeta en el posnet de Mercado Pago
    TARJETA_MP,
    CUENTA_DNI,
    // tarjeta en otro posnet (Clover u otro)
    TARJETA,
    BONIFICACION
}
