package com.LosCiruelos.padel_club_api.ventas.entity.enums;

/** Cómo se cobró una venta suelta en el club. */
public enum MedioDePago {
    EFECTIVO,
    // QR de Mercado Pago (o transferencia a la cuenta del club)
    MERCADO_PAGO,
    // Cuenta DNI (Banco Provincia)
    CUENTA_DNI
}
