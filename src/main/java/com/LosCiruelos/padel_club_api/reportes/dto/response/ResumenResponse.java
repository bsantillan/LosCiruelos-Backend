package com.LosCiruelos.padel_club_api.reportes.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.LosCiruelos.padel_club_api.reportes.repository.ProductoVendido;

/**
 * Resumen del período para el panel del club. jugados, porJugar, cancelaciones y productos
 * toman las reservas cuyo turno cae entre desde y hasta; caja, los pagos cobrados y devueltos
 * en esas fechas (cierre de caja).
 */
public record ResumenResponse(
        LocalDate desde,
        LocalDate hasta,
        Jugados jugados,
        PorJugar porJugar,
        Caja caja,
        Cancelaciones cancelaciones,
        List<ProductoVendido> productos) {

    /** Turnos confirmados que ya terminaron. deuda = montoTotal - montoCobrado - montoBonificado. */
    public record Jugados(long turnos, BigDecimal montoTotal, BigDecimal montoCobrado, BigDecimal montoBonificado,
            BigDecimal deuda) {
    }

    /** Turnos confirmados que todavía no terminaron. aCobrar = montoTotal - cobradoAnticipado - montoBonificado. */
    public record PorJugar(long turnos, BigDecimal montoTotal, BigDecimal cobradoAnticipado,
            BigDecimal montoBonificado, BigDecimal aCobrar) {
    }

    /** Plata que entró y salió en el período. total = efectivo + MP online + MP presencial; neto = total - devoluciones. */
    public record Caja(BigDecimal total, BigDecimal efectivo, BigDecimal mercadoPagoOnline,
            BigDecimal mercadoPagoPresencial, BigDecimal devoluciones, BigDecimal neto) {
    }

    /** Reservas canceladas del período y lo pagado que el club no devolvió. */
    public record Cancelaciones(long turnos, BigDecimal retenido) {
    }
}
