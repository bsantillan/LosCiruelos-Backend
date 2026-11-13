package com.LosCiruelos.padel_club_api.reservas.dto.response;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;

/**
 * Métricas del club para el ADMIN (GET /admin/metricas). Los bloques que se comparan traen el valor del
 * período pedido, el del período anterior de igual duración y la variación en %.
 *
 * Lo de reservas va por la fecha del turno (de {@code desde} a {@code hasta}); lo de ventas sueltas, por
 * jornada. Las reservas "confirmadas" son las que se juegan o se jugaron: CONFIRMADA, SEÑADA,
 * PARCIALMENTE_PAGADA, PAGADA y COMPLETADA.
 */
public record MetricasResponse(
        LocalDate desde,
        LocalDate hasta,
        Periodo anterior,
        Ocupacion ocupacion,
        Ingresos ingresos,
        Clientes clientes,
        Reservas reservas,
        Cancelaciones cancelaciones,
        Deuda deuda,
        Futuro futuro,
        Productos productos) {

    public record Periodo(LocalDate desde, LocalDate hasta) {
    }

    /**
     * Un valor del período, el del anterior y la variación.
     *
     * @param variacion (actual − anterior) / anterior × 100, con un decimal; null si el anterior es 0
     */
    public record Comparado(BigDecimal actual, BigDecimal anterior, BigDecimal variacion) {
    }

    // ── Ocupación ────────────────────────────────────────────────────────

    /**
     * @param porcentaje    horas ocupadas sobre las horas abiertas (todas las canchas), en %
     * @param horasAbiertas horas en que el club abre por la cantidad de canchas (sin los días cerrados)
     * @param mapa          día de la semana × franja de 1 hora, con el % ocupado (horas pico y muertas)
     * @param porFranja     mañana (antes de las 12), tarde (12 a 18) y noche (desde las 18)
     * @param proximos7Dias desde hoy, con lo que ya está confirmado o pendiente de pago
     */
    public record Ocupacion(Comparado porcentaje, Comparado horasOcupadas, Comparado horasAbiertas,
            List<OcupacionCancha> porCancha, List<DiaDelMapa> mapa, List<OcupacionFranja> porFranja,
            List<OcupacionDia> proximos7Dias) {
    }

    /** @param ingresos lo facturado de las reservas confirmadas de esa cancha (cancha y productos) */
    public record OcupacionCancha(Long canchaId, Integer numero, BigDecimal porcentaje, BigDecimal horasOcupadas,
            BigDecimal ingresos) {
    }

    public record DiaDelMapa(DayOfWeek dia, List<FranjaDelMapa> franjas) {
    }

    /** @param hora inicio de la franja de 1 hora ("08:00"); porcentaje null si el club no abre a esa hora */
    public record FranjaDelMapa(String hora, BigDecimal porcentaje) {
    }

    /**
     * @param franja           MANIANA, TARDE o NOCHE
     * @param ingresoPorHora   lo facturado de la cancha en esa franja sobre sus horas ocupadas
     */
    public record OcupacionFranja(String franja, BigDecimal porcentaje, BigDecimal horasOcupadas,
            BigDecimal ingresoPorHora) {
    }

    public record OcupacionDia(LocalDate fecha, BigDecimal porcentaje, BigDecimal horasOcupadas,
            BigDecimal horasAbiertas) {
    }

    // ── Ingresos ─────────────────────────────────────────────────────────

    /**
     * Lo facturado: las reservas confirmadas del período (cancha y productos) y las ventas sueltas
     * (netas de devoluciones).
     *
     * @param online          lo cobrado por Mercado Pago online (el cliente desde la web) de esas reservas
     * @param enElClub        lo cobrado en el club (efectivo, QR, transferencia, Cuenta DNI) de esas reservas
     * @param ticketPromedio  (cancha + productos) por reserva confirmada
     * @param porHoraOcupada  lo facturado de cancha por hora ocupada
     */
    public record Ingresos(Comparado total, Comparado cancha, Comparado productosDeReservas, Comparado ventasSueltas,
            Comparado online, Comparado enElClub, Comparado ticketPromedio, Comparado porHoraOcupada) {
    }

    // ── Clientes ─────────────────────────────────────────────────────────

    /**
     * @param activos        con al menos una reserva confirmada en el período
     * @param nuevos         activos cuya primera reserva confirmada es de este período
     * @param recurrentes    activos que ya habían reservado antes del período
     * @param frecuencia     reservas confirmadas por cliente activo
     * @param dejaronDeVenir 3 o más reservas en los 90 días anteriores a los últimos 30 y ninguna en los últimos 30
     *                       (contados desde hoy)
     */
    public record Clientes(Comparado activos, Comparado nuevos, Comparado recurrentes, Comparado frecuencia,
            List<ClienteDestacado> topPorReservas, List<ClienteDestacado> topPorGasto,
            List<ClienteQueDejoDeVenir> dejaronDeVenir) {
    }

    public record ClienteDestacado(Long clienteId, String nombre, long reservas, BigDecimal gasto) {
    }

    public record ClienteQueDejoDeVenir(Long clienteId, String nombre, String telefono, long reservasAntes,
            LocalDate ultimaReserva) {
    }

    // ── Reservas ─────────────────────────────────────────────────────────

    /**
     * Las creadas en el período (por fecha de creación), sin los partidos que genera solo un turno fijo.
     *
     * @param anticipacionPromedioHoras horas promedio entre que se crea la reserva y empieza el turno
     * @param duraciones                minutos de turno, de la más elegida a la menos
     * @param porTipo                   de las confirmadas del período
     * @param porOrigen                 quién las creó: el cliente desde la web o el personal
     */
    public record Reservas(Comparado creadas, Comparado anticipacionPromedioHoras, Anticipacion anticipacion,
            List<Duracion> duraciones, PorTipo porTipo, PorOrigen porOrigen) {
    }

    public record Anticipacion(long mismoDia, long unoADosDias, long tresASieteDias, long masDeSieteDias) {
    }

    public record Duracion(long minutos, long cantidad) {
    }

    public record PorTipo(long normales, long partidos, long turnosFijos) {
    }

    public record PorOrigen(long cliente, long personal) {
    }

    // ── Cancelaciones ────────────────────────────────────────────────────

    /**
     * De las reservas del período (por fecha del turno).
     *
     * @param tasa                canceladas / (canceladas + confirmadas), en %
     * @param porCliente          las canceló el cliente
     * @param porClub             las canceló el personal o el sistema (cambio de horario, reembolso total)
     * @param sinDato             canceladas antes de que se registrara quién y cuándo
     * @param anticipacion        cuánto antes del turno se cancelaron (las que tienen el dato)
     * @param retenido            lo pagado de las canceladas que el club no devolvió
     * @param vencidasSinPagar    PENDIENTE que vencieron sin pagar
     */
    public record Cancelaciones(Comparado tasa, Comparado canceladas, long porCliente, long porClub, long sinDato,
            AnticipacionCancelacion anticipacion, Comparado retenido, Comparado vencidasSinPagar) {
    }

    public record AnticipacionCancelacion(long menosDe2Horas, long de2a24Horas, long de1a3Dias, long masDe3Dias) {
    }

    // ── Deuda ────────────────────────────────────────────────────────────

    /** Turnos ya jugados que deben saldo (a hoy, sin importar el período), por antigüedad del turno. */
    public record Deuda(BigDecimal total, BigDecimal hasta7Dias, BigDecimal de8a30Dias, BigDecimal masDe30Dias,
            List<Deudor> deudores) {
    }

    public record Deudor(Long clienteId, String nombre, String telefono, BigDecimal monto, long reservas,
            LocalDate masVieja) {
    }

    // ── Futuro ───────────────────────────────────────────────────────────

    /**
     * @param asegurado          lo ya cobrado (seña o total) de los turnos confirmados que todavía no se jugaron
     * @param porCobrar14Dias    lo que falta cobrar de los turnos confirmados de los próximos 14 días
     * @param turnos14Dias       cuántos turnos confirmados hay en los próximos 14 días
     */
    public record Futuro(BigDecimal asegurado, BigDecimal porCobrar14Dias, long turnos14Dias) {
    }

    // ── Productos ────────────────────────────────────────────────────────

    /**
     * @param masVendidos       productos de venta (en reservas y ventas sueltas), del que más se vendió al que menos
     * @param alquilados        alquileres (paletas) en reservas
     * @param noDevueltos       alquileres con la devolución registrada y menos unidades devueltas (roturas o pérdidas)
     * @param stockBajo         productos de venta activos con {@value com.LosCiruelos.padel_club_api.reservas.service.CalculoDeMetricas#STOCK_BAJO}
     *                          unidades o menos
     */
    public record Productos(List<ProductoMovido> masVendidos, List<ProductoMovido> alquilados, NoDevueltos noDevueltos,
            List<ProductoConPocoStock> stockBajo) {
    }

    public record ProductoMovido(Long productoId, String nombre, TipoProducto tipo, long cantidad, BigDecimal monto) {
    }

    /**
     * @param unidades           unidades que no volvieron
     * @param montoAlquiler      lo que se cobró de alquiler por esas unidades (el sistema no guarda el costo de reponerlas)
     * @param sinRegistrar       reservas con el turno terminado que todavía no registraron la devolución
     */
    public record NoDevueltos(long unidades, BigDecimal montoAlquiler, long reservas, long sinRegistrar) {
    }

    public record ProductoConPocoStock(Long productoId, String nombre, int stock) {
    }
}
