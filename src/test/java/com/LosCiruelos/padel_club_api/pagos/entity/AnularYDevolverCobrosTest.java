package com.LosCiruelos.padel_club_api.pagos.entity;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.reserva;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.ConceptoImputacion;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoDevolucion;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaProducto;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

/** Anular y devolver cobros. La reserva: cancha de $15.000 y 2 Gatorade de $2.000 (línea 51). */
class AnularYDevolverCobrosTest {

    private final Usuario admin = usuario(3L, Role.ADMIN);
    private Reserva reserva;
    private ReservaProducto gatorade;

    @BeforeEach
    void unaReserva() {
        reserva = reserva(10L, usuario(1L, Role.CLIENTE), EstadoReserva.SEÑADA, "19000");
        Producto producto = Producto.builder().id(5L).nombre("Gatorade").tipo(TipoProducto.VENTA)
                .precio(new BigDecimal("2000")).stock(10).build();
        gatorade = ReservaProducto.builder().id(51L).reserva(reserva).producto(producto).cantidad(2)
                .precioUnitario(new BigDecimal("2000")).build();
        reserva.getProductos().add(gatorade);
    }

    private Pago cobrado(PaymentProvider medio, String monto) {
        Pago pago = TestData.cobrado(1L, reserva, monto);
        pago.setProvider(medio);
        pago.setRegistradoPorId(2L);
        pago.setPaidAt(AHORA.minusHours(1));
        return pago;
    }

    private static PagoImputacion.Pedido cancha(String monto) {
        return new PagoImputacion.Pedido(ConceptoImputacion.CANCHA, null, new BigDecimal(monto));
    }

    private static PagoImputacion.Pedido linea(Long id, String monto) {
        return new PagoImputacion.Pedido(ConceptoImputacion.PRODUCTO, id, new BigDecimal(monto));
    }

    // ── Anular ───────────────────────────────────────────────────────────

    @Test
    void unCobroEnEfectivoSeAnulaYDejaDeCubrir() {
        Pago pago = cobrado(PaymentProvider.MANUAL, "17000");

        pago.anular(admin, "Error de carga", AHORA);

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.ANULADO);
        assertThat(pago.getMotivoAnulacion()).isEqualTo("Error de carga");
        assertThat(pago.getAnuladoPor()).isSameAs(admin);
        assertThat(pago.getAnuladoAt()).isEqualTo(AHORA);
        assertThat(pago.getImputaciones()).isEmpty();
        assertThat(pago.getMontoRetenido()).isEqualByComparingTo("0");
        assertThat(reserva.getMontoCubierto()).isEqualByComparingTo("0");
    }

    @Test
    void unCobroDeMercadoPagoNoSeAnula() {
        Pago qr = cobrado(PaymentProvider.MERCADO_PAGO, "15000");
        qr.setOrderId("ORD-1");

        assertThatThrownBy(() -> qr.anular(admin, "Error", AHORA))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Un cobro de Mercado Pago (online o con el QR) no se anula: la plata entró, hay que devolverla.");
        assertThat(qr.getEstado()).isEqualTo(EstadoPago.APROBADO);
    }

    @Test
    void unCobroConDevolucionesNoSeAnula() {
        Pago pago = cobrado(PaymentProvider.MANUAL, "17000");
        pago.devolver(List.of(linea(51L, "2000")), null, admin, AHORA);

        assertThatThrownBy(() -> pago.anular(admin, "Error", AHORA))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("El cobro tiene devoluciones: no se puede anular.");
    }

    @Test
    void elEmpleadoSoloAnulaLoQueSeRegistroEnLaJornada() {
        Pago deHoy = cobrado(PaymentProvider.MANUAL, "7500");
        var config = TestData.configuracion();

        assertThat(deHoy.seRegistroEnLaJornada(config.jornadaEn(AHORA))).isTrue();
        // a las 00:30 del día siguiente sigue la jornada de hoy
        deHoy.setPaidAt(TestData.HOY.atTime(22, 0));
        assertThat(deHoy.seRegistroEnLaJornada(config.jornadaEn(TestData.HOY.plusDays(1).atTime(0, 30)))).isTrue();
        // uno de ayer a la noche no es de la jornada de hoy
        deHoy.setPaidAt(TestData.HOY.minusDays(1).atTime(21, 0));
        assertThat(deHoy.seRegistroEnLaJornada(config.jornadaEn(AHORA))).isFalse();
        // uno que no registró el personal (el pago online del cliente), tampoco
        deHoy.setPaidAt(AHORA.minusHours(1));
        deHoy.setRegistradoPorId(null);
        assertThat(deHoy.seRegistroEnLaJornada(config.jornadaEn(AHORA))).isFalse();
    }

    // ── Devolver ─────────────────────────────────────────────────────────

    @Test
    void unaDevolucionPorLineaReduceLoQueCubreYQuedaPorEntregar() {
        Pago pago = cobrado(PaymentProvider.MANUAL, "19000"); // cancha 15000 + Gatorade 4000

        Devolucion d = pago.devolver(List.of(linea(51L, "2000"), cancha("1000")), "Un Gatorade vencido", admin, AHORA);
        pago.actualizarEstadoPorDevoluciones(AHORA);

        assertThat(d.getMonto()).isEqualByComparingTo("3000");
        assertThat(d.getEstado()).isEqualTo(EstadoDevolucion.A_REEMBOLSAR);
        assertThat(d.getLineas()).extracting(DevolucionLinea::getNombre, l -> l.getMonto().toPlainString())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Gatorade", "2000"),
                        org.assertj.core.groups.Tuple.tuple("Cancha", "1000"));
        assertThat(pago.getMontoReembolsado()).isEqualByComparingTo("3000");
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO); // todavía retiene $16.000
        assertThat(reserva.pagadoDe(gatorade)).isEqualByComparingTo("2000");
        assertThat(reserva.getMontoCanchaPagado()).isEqualByComparingTo("14000");
        assertThat(reserva.getMontoPendiente()).isEqualByComparingTo("3000");
    }

    @Test
    void devolverTodoDejaElCobroPorDevolverYAlEntregarseQuedaDevuelto() {
        Pago pago = cobrado(PaymentProvider.CUENTA_DNI, "17000"); // cancha 15000 + Gatorade 2000

        Devolucion d = pago.devolver(List.of(cancha("15000"), linea(51L, "2000")), null, admin, AHORA);
        pago.actualizarEstadoPorDevoluciones(AHORA);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
        assertThat(pago.getImputaciones()).isEmpty();

        d.entregar(admin, AHORA.plusHours(1));
        pago.actualizarEstadoPorDevoluciones(AHORA.plusHours(1));
        assertThat(d.getEstado()).isEqualTo(EstadoDevolucion.DEVUELTO);
        assertThat(d.getEntregadoAt()).isEqualTo(AHORA.plusHours(1));
        assertThat(d.getEntregadoPor()).isSameAs(admin);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.DEVUELTO);
        assertThatThrownBy(() -> d.entregar(admin, AHORA)).isInstanceOf(EstadoInvalidoException.class);
    }

    @Test
    void noSeDevuelveMasDeLoQueElCobroLeCubreALaLinea() {
        Pago pago = cobrado(PaymentProvider.MANUAL, "16000"); // cancha 15000 + $1000 del Gatorade

        assertThatThrownBy(() -> pago.devolver(List.of(linea(51L, "2000")), null, admin, AHORA))
                .isInstanceOf(ReservaException.class)
                .hasMessage("El cobro #1 le cubre $1.000 a Gatorade: no se pueden devolver $2.000.");
        assertThatThrownBy(() -> pago.devolver(List.of(linea(99L, "100")), null, admin, AHORA))
                .hasMessage("La línea #99 no está cubierta por el cobro #1.");
        assertThatThrownBy(() -> pago.devolver(List.of(cancha("100"), cancha("100")), null, admin, AHORA))
                .hasMessage("La cancha está repetida en la devolución.");
        assertThat(pago.getDevoluciones()).isEmpty();
    }

    @Test
    void unaBonificacionNoSeDevuelve() {
        Pago bonificacion = cobrado(PaymentProvider.BONIFICACION, "5000");

        assertThatThrownBy(() -> bonificacion.devolver(List.of(cancha("5000")), null, admin, AHORA))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("Una bonificación no es plata: no se devuelve, se anula.");
    }

    @Test
    void alCancelarSeLiberaEnProporcion() {
        Pago pago = cobrado(PaymentProvider.MANUAL, "10000"); // cancha 10000
        pago.imputar(List.of(PagoImputacion.aCancha(new BigDecimal("8000")),
                PagoImputacion.aLinea(gatorade, new BigDecimal("2000"))));

        Devolucion d = pago.devolverEnProporcion(new BigDecimal("5000"), "Cancelación de la reserva", null, AHORA);

        assertThat(d.getMonto()).isEqualByComparingTo("5000");
        assertThat(d.getLineas()).extracting(l -> l.getMonto().toPlainString()).containsExactly("4000.00", "1000.00");
        assertThat(pago.getMontoReembolsado()).isEqualByComparingTo("5000");
        assertThat(cubierto(pago)).isEqualByComparingTo("5000");
    }

    @Test
    void enProporcionLosCentavosNoSePierden() {
        Pago pago = cobrado(PaymentProvider.MANUAL, "10000");
        pago.imputar(List.of(PagoImputacion.aCancha(new BigDecimal("3333.33")),
                PagoImputacion.aLinea(gatorade, new BigDecimal("3333.33")),
                PagoImputacion.aCancha(new BigDecimal("3333.34"))));

        Devolucion d = pago.devolverEnProporcion(new BigDecimal("1000"), null, null, AHORA);

        assertThat(com.LosCiruelos.padel_club_api.common.Dinero.sumar(d.getLineas().stream().map(DevolucionLinea::getMonto)))
                .isEqualByComparingTo("1000");
        assertThat(cubierto(pago)).isEqualByComparingTo("9000");
    }

    private static BigDecimal cubierto(Pago pago) {
        return com.LosCiruelos.padel_club_api.common.Dinero.sumar(pago.getImputaciones().stream()
                .map(PagoImputacion::getMonto));
    }

    // ── Estado de la reserva ─────────────────────────────────────────────

    @Test
    void unaReservaConfirmadaQueQuedaSinNadaCubiertoQuedaConfirmadaYNoVence() {
        Pago pago = cobrado(PaymentProvider.MANUAL, "9500");
        reserva.setExpiresAt(AHORA.plusMinutes(10));

        pago.anular(admin, "Error de carga", AHORA);
        boolean cambio = reserva.recalcularTrasDeshacerUnCobro(new BigDecimal("9500"), AHORA);

        assertThat(cambio).isTrue();
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(reserva.getExpiresAt()).isNull();
        assertThat(reserva.estaVencida(AHORA.plusDays(1))).isFalse();
    }

    @Test
    void despuesDeAnularHastaCeroSigueConfirmadaAunqueCambieElTotal() {
        Pago pago = cobrado(PaymentProvider.MANUAL, "9500");
        pago.anular(admin, "Error de carga", AHORA);
        reserva.recalcularTrasDeshacerUnCobro(new BigDecimal("9500"), AHORA);

        reserva.getProductos().remove(gatorade);
        reserva.setMontoTotal(new BigDecimal("15000"));
        reserva.recalcularPorCambioDeTotal(new BigDecimal("7500"), AHORA);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    void unaPagadaQueDevuelveUnaParteVuelveASenada() {
        reserva.setEstado(EstadoReserva.PAGADA);
        Pago pago = cobrado(PaymentProvider.MANUAL, "19000");

        pago.devolver(List.of(linea(51L, "4000")), null, admin, AHORA);
        reserva.recalcularTrasDeshacerUnCobro(new BigDecimal("9500"), AHORA);

        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
    }
}
