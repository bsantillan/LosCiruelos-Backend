package com.LosCiruelos.padel_club_api.pagos.dto.response;

import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

/**
 * Un medio con que el personal cobra una reserva, para el modal de cobro (los mismos 4 que en las ventas).
 *
 * @param grupo   cómo se agrupa en el panel: EFECTIVO, MERCADO_PAGO (QR, transferencia y tarjeta en su
 *                posnet), CUENTA_DNI, TARJETA (otro posnet) o BONIFICACION
 * @param soloAdmin solo lo puede usar el ADMIN (la bonificación)
 * @param opcion  cómo se muestra dentro de su grupo ("Transferencia")
 * @param detalle por qué no está disponible (null si lo está)
 * @param metodo  y {@code ruta}: el endpoint para cobrar con este medio ({id} es el de la reserva)
 */
public record MedioDeCobroResponse(MedioDePago medio, String etiqueta, String grupo, String opcion,
        boolean soloAdmin, boolean disponible, boolean conQr, String detalle, String metodo, String ruta) {
}
