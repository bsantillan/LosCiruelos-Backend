package com.LosCiruelos.padel_club_api.ventas.dto.response;

import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

/**
 * Un medio con que se puede cobrar una venta suelta, para el selector del panel.
 *
 * @param grupo   cómo se agrupa en el panel: EFECTIVO, MERCADO_PAGO (QR, transferencia y tarjeta en su
 *                posnet), CUENTA_DNI, TARJETA (otro posnet) o BONIFICACION
 * @param soloAdmin solo lo puede usar el ADMIN (la bonificación)
 * @param opcion  cómo se muestra dentro de su grupo ("Transferencia")
 * @param conQr   se cobra con el QR de la caja (la venta queda esperando el pago)
 * @param detalle por qué no está disponible (null si lo está)
 */
public record MedioDeVentaResponse(MedioDePago medio, String etiqueta, String grupo, String opcion,
        boolean soloAdmin, boolean disponible, boolean conQr, String detalle) {
}
