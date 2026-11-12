package com.LosCiruelos.padel_club_api.pagos.dto.response;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.ConceptoImputacion;

/**
 * Qué cubre una parte de un pago.
 *
 * @param lineaId id de la línea de producto de la reserva (null si es la cancha)
 * @param nombre  "Cancha" o el nombre del producto
 */
public record ImputacionResponse(ConceptoImputacion concepto, Long lineaId, String nombre, BigDecimal monto) {
}
