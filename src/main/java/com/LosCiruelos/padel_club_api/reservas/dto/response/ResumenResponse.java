package com.LosCiruelos.padel_club_api.reservas.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.LosCiruelos.padel_club_api.reservas.repository.ProductoVendido;

/**
 * Resumen del período para el panel del club. jugados, porJugar y cancelaciones toman las
 * reservas cuyo turno cae entre desde y hasta; caja, los pagos cobrados y devueltos y las ventas
 * sueltas de esas fechas (cierre de caja); productos, lo pedido en esas reservas más lo vendido
 * suelto en esas fechas.
 */
public record ResumenResponse(
        LocalDate desde,
        LocalDate hasta,
        Jugados jugados,
        PorJugar porJugar,
        Caja caja,
        Cancelaciones cancelaciones,
        Ventas ventas,
        List<ProductoVendido> productos) {

    /** Turnos confirmados que ya terminaron. deuda = montoTotal - montoCobrado - montoBonificado. */
    public record Jugados(long turnos, BigDecimal montoTotal, BigDecimal montoCobrado, BigDecimal montoBonificado,
            BigDecimal deuda) {
    }

    /** Turnos confirmados que todavía no terminaron. aCobrar = montoTotal - cobradoAnticipado - montoBonificado. */
    public record PorJugar(long turnos, BigDecimal montoTotal, BigDecimal cobradoAnticipado,
            BigDecimal montoBonificado, BigDecimal aCobrar) {
    }

    /**
     * Plata que entró y salió en el período, incluidas las ventas sueltas, por medio: efectivo y lo
     * que entra a una cuenta (Mercado Pago, sea online o en el club, y Cuenta DNI).
     * total = efectivo + mercadoPago + cuentaDni; neto = total - devoluciones.
     */
    public record Caja(BigDecimal total, BigDecimal efectivo, BigDecimal mercadoPago, BigDecimal cuentaDni,
            BigDecimal devoluciones, BigDecimal neto) {
    }

    /** Reservas canceladas del período y lo pagado que el club no devolvió. */
    public record Cancelaciones(long turnos, BigDecimal retenido) {
    }

    /** Ventas sueltas del período (sin reserva): cuántas y cuánto se cobró. Ya están sumadas en la caja. */
    public record Ventas(long cantidad, BigDecimal total, BigDecimal devoluciones) {
    }
}
