package com.LosCiruelos.padel_club_api.pagos.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.CanalDePago;

/** Una devolución que hay que darle al cliente a mano (A_REEMBOLSAR), con su reserva y el medio original. */
public record DevolucionAEntregarResponse(Long devolucionId, Long reservaId, LocalDate fechaReserva, Long pagoId,
        Long clienteId, String cliente, BigDecimal monto, CanalDePago medio, String motivo, LocalDateTime creadoAt) {
}
