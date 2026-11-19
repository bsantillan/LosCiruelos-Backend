package com.LosCiruelos.padel_club_api.pagos.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.MedioDeCaja;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.TipoMovimientoCaja;

/**
 * El cierre de caja de una jornada (de la apertura de {@code fecha} a la apertura del día siguiente).
 *
 * @param cobrado        reservas y ventas sueltas, sin lo anulado
 * @param devuelto       las devoluciones entregadas en la jornada (o confirmadas por Mercado Pago), por el
 *                       medio del cobro original
 * @param efectivoEnCaja cobrado.efectivo − devuelto.efectivo
 * @param bonificado     lo bonificado en la jornada (no es plata)
 * @param aEntregar      las devoluciones de la jornada que todavía hay que darle al cliente
 * @param movimientos    todo lo que movió plata, en orden de fecha
 */
public record CajaResponse(LocalDate fecha, LocalDateTime inicio, LocalDateTime fin, PorMedio cobrado,
        PorMedio devuelto, BigDecimal efectivoEnCaja, BigDecimal bonificado, AEntregar aEntregar,
        List<Movimiento> movimientos) {

    public record PorMedio(BigDecimal efectivo, BigDecimal mercadoPago, BigDecimal cuentaDni, BigDecimal total) {
    }

    public record AEntregar(long cantidad, BigDecimal monto) {
    }

    /**
     * @param monto         positivo si entra, negativo si sale (devoluciones y anulaciones)
     * @param detalle       "Reserva #12 · Ana Pérez", "Venta #8"
     * @param registradoPor quién lo hizo (null: el cliente o Mercado Pago)
     */
    public record Movimiento(LocalDateTime fecha, TipoMovimientoCaja tipo, MedioDeCaja medio, BigDecimal monto,
            String detalle, Long reservaId, Long ventaId, String registradoPor) {
    }
}
