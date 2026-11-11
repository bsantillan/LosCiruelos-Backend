package com.LosCiruelos.padel_club_api.pagos.payment;

public enum PaymentProvider {
    MERCADO_PAGO,
    MANUAL,
    // el ADMIN no le cobra al cliente (todo o una parte): cubre el monto pero no es plata
    BONIFICACION,
    // Cuenta DNI (Banco Provincia): no tiene API, lo carga el personal y lo verifica el ADMIN contra la cuenta
    CUENTA_DNI,
    // transferencia a la cuenta de Mercado Pago del club (alias o CVU): la carga el personal y la verifica el ADMIN
    TRANSFERENCIA_MP;

    /** Se cobra a mano y el ADMIN lo verifica contra la cuenta del club. */
    public boolean seVerifica() {
        return this == CUENTA_DNI || this == TRANSFERENCIA_MP;
    }
}
