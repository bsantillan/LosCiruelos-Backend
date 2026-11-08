package com.LosCiruelos.padel_club_api.ventas.entity.enums;

/** Cómo se cobra una venta suelta en el club, en el orden en que se muestran. */
public enum MedioDePago {
    EFECTIVO("Efectivo", false),
    // con el QR de la caja de Mercado Pago (se confirma solo)
    MERCADO_PAGO("Mercado Pago (QR de la caja)", true),
    // Cuenta DNI (Banco Provincia): se cobra a mano y el ADMIN lo verifica
    CUENTA_DNI("Cuenta DNI", false);

    private final String etiqueta;
    private final boolean conQr;

    MedioDePago(String etiqueta, boolean conQr) {
        this.etiqueta = etiqueta;
        this.conQr = conQr;
    }

    /** Cómo se muestra en el panel. */
    public String etiqueta() {
        return etiqueta;
    }

    /** Se cobra con el QR de la caja: solo se puede si está configurado. */
    public boolean conQr() {
        return conQr;
    }
}
