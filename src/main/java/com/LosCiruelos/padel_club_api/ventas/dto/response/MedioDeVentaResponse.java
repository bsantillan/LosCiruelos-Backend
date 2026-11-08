package com.LosCiruelos.padel_club_api.ventas.dto.response;

import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

/**
 * Un medio con que se puede cobrar una venta suelta, para el selector del panel.
 *
 * @param conQr   se cobra con el QR de la caja (la venta queda esperando el pago)
 * @param detalle por qué no está disponible (null si lo está)
 */
public record MedioDeVentaResponse(MedioDePago medio, String etiqueta, boolean disponible, boolean conQr,
        String detalle) {
}
