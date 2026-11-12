package com.LosCiruelos.padel_club_api.pagos.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.CanalDePago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoDevolucion;

/**
 * Una devolución en los listados del personal (las que hay que entregar y las que el ADMIN tiene que
 * revisar), con su reserva, el cliente y el medio del cobro original.
 *
 * @param registradaPor quién la registró (null: el sistema)
 */
public record DevolucionListadoResponse(Long devolucionId, Long reservaId, LocalDate fechaReserva, Long pagoId,
        Long clienteId, String cliente, BigDecimal monto, CanalDePago medio, EstadoDevolucion estado, String motivo,
        LocalDateTime creadoAt, String registradaPor, boolean revisada) {
}
