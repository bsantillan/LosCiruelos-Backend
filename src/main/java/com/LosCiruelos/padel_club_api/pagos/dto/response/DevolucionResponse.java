package com.LosCiruelos.padel_club_api.pagos.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoDevolucion;

/**
 * Lo que se devolvió (o hay que devolver) de un cobro.
 *
 * @param lineas      cuánto de cada línea (la cancha o un producto); vacía en las de antes de las devoluciones
 *                    por línea y en los cobros de Mercado Pago que no se aplicaron
 * @param entregadoAt cuándo se le dio al cliente o Mercado Pago lo confirmó (null: todavía no)
 * @param usuario     quién la registró (null: el sistema, por ejemplo al cancelar el cliente)
 */
public record DevolucionResponse(Long id, BigDecimal monto, List<ImputacionResponse> lineas, EstadoDevolucion estado,
        String motivo, LocalDateTime creadoAt, LocalDateTime entregadoAt, String usuario, String entregadoPor) {
}
