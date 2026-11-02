package com.LosCiruelos.padel_club_api.pagos.payment;

public enum PaymentProvider {
    MERCADO_PAGO,
    MANUAL,
    // el ADMIN no le cobra al cliente (todo o una parte): cubre el monto pero no es plata
    BONIFICACION,
    // Cuenta DNI (Banco Provincia), cargado a mano con el número de comprobante: no tiene API
    CUENTA_DNI
}
