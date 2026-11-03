package com.LosCiruelos.padel_club_api.pagos.payment;

public enum PaymentProvider {
    MERCADO_PAGO,
    MANUAL,
    // el ADMIN no le cobra al cliente (todo o una parte): cubre el monto pero no es plata
    BONIFICACION,
    // Cuenta DNI (Banco Provincia): no tiene API, lo carga el personal y lo verifica el ADMIN contra la cuenta
    CUENTA_DNI
}
