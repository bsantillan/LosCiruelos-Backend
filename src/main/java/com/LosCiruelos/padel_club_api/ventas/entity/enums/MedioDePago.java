package com.LosCiruelos.padel_club_api.ventas.entity.enums;

/**
 * Cómo se cobra en el club (una venta suelta o una reserva), en el orden en que se muestran. En el
 * panel se agrupan por {@code MedioDeCaja}: Efectivo, Mercado Pago (QR, transferencia o tarjeta en su
 * posnet), Cuenta DNI y Tarjeta (otro posnet). BONIFICACION es solo de las ventas sueltas (una reserva
 * se bonifica con su propio endpoint) y no es plata.
 */
public enum MedioDePago {
    EFECTIVO("Efectivo", "Efectivo", false),
    // con el QR de la caja de Mercado Pago (se confirma solo)
    MERCADO_PAGO("Mercado Pago (QR de la caja)", "QR de la caja", true),
    // transferencia a la cuenta de Mercado Pago del club (alias o CVU): cobrada al momento y la verifica el ADMIN
    TRANSFERENCIA_MP("Mercado Pago (transferencia)", "Transferencia", false),
    // tarjeta de débito o crédito en el posnet de Mercado Pago (Point): la plata entra a la cuenta de MP
    TARJETA_MP("Mercado Pago (tarjeta en el posnet)", "Tarjeta en el posnet", false),
    // Cuenta DNI (Banco Provincia): se cobra a mano y el ADMIN lo verifica
    CUENTA_DNI("Cuenta DNI", "Cuenta DNI", false),
    // tarjeta en otro posnet (Clover u otro): la plata entra al banco de ese posnet
    TARJETA("Tarjeta (otro posnet)", "Tarjeta", false),
    // solo ADMIN, solo en las ventas: no se le cobra al cliente. Los productos se entregan pero no es plata
    BONIFICACION("Bonificación del club", "Bonificación", false);

    private final String etiqueta;
    private final String opcion;
    private final boolean conQr;

    MedioDePago(String etiqueta, String opcion, boolean conQr) {
        this.etiqueta = etiqueta;
        this.opcion = opcion;
        this.conQr = conQr;
    }

    /** Cómo se muestra en el panel ("Mercado Pago (transferencia)"). */
    public String etiqueta() {
        return etiqueta;
    }

    /** Cómo se muestra dentro de su grupo ("Transferencia" dentro de Mercado Pago). */
    public String opcion() {
        return opcion;
    }

    /**
     * Se cobra a mano y el ADMIN lo verifica contra la cuenta del club. Las tarjetas no: el posnet
     * aprueba el cobro en el momento.
     */
    public boolean seVerifica() {
        return this == CUENTA_DNI || this == TRANSFERENCIA_MP;
    }

    /** Es plata que entra (todos menos la bonificación): cuenta en la caja y en lo vendido. */
    public boolean esPlata() {
        return this != BONIFICACION;
    }

    /** Solo lo puede usar el ADMIN (la bonificación). */
    public boolean soloAdmin() {
        return this == BONIFICACION;
    }

    /** Se cobra con el QR de la caja: solo se puede si está configurado. */
    public boolean conQr() {
        return conQr;
    }
}
