package com.LosCiruelos.padel_club_api.pagos.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.HOY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.club.service.JornadaService;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.mapper.ReservaMapper;
import com.LosCiruelos.padel_club_api.pagos.dto.request.DevolucionRequest;
import com.LosCiruelos.padel_club_api.pagos.dto.request.LineaDevolucionRequest;
import com.LosCiruelos.padel_club_api.pagos.entity.Devolucion;
import com.LosCiruelos.padel_club_api.pagos.entity.DevolucionLinea;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.PagoImputacion;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.ConceptoImputacion;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoDevolucion;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.exception.PagoProveedorException;
import com.LosCiruelos.padel_club_api.pagos.exception.ReembolsoFallidoException;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaProducto;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

/** Anular, devolver y entregar cobros. La reserva: cancha de $15.000 y un Gatorade de $2.000 (línea 51). */
class AnulacionYDevolucionServiceTest extends PagosTestBase {

    private Usuario admin;
    private Reserva reserva;

    @BeforeEach
    void unaReservaConUnProducto() {
        admin = TestData.usuario(3L, Role.ADMIN);
        when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
        reserva = reservaBloqueada(EstadoReserva.SEÑADA);
        Producto gatorade = Producto.builder().id(5L).nombre("Gatorade").tipo(TipoProducto.VENTA)
                .precio(new BigDecimal("2000")).stock(10).build();
        reserva.getProductos().add(ReservaProducto.builder().id(51L).reserva(reserva).producto(gatorade).cantidad(1)
                .precioUnitario(new BigDecimal("2000")).build());
        reserva.setMontoTotal(new BigDecimal("17000"));
    }

    private AnulacionService anulacion(LocalDateTime ahora) {
        return new AnulacionService(reservaService, usuarioService, TestData.jornadas(TestData.reloj(ahora)), estados,
                mock(ReservaMapper.class), TestData.reloj(ahora));
    }

    private DevolucionService devoluciones(LocalDateTime ahora) {
        return new DevolucionService(reservaService, usuarioService, TestData.jornadas(TestData.reloj(ahora)),
                pagoRepository, estados, new DevolucionesPorApi(factory, proveedorQr), mock(ReservaMapper.class),
                TestData.reloj(ahora));
    }

    /** Un cobro del club con la imputación automática, registrado por el empleado en {@code cuando}. */
    private Pago cobro(Long id, PaymentProvider medio, String monto, LocalDateTime cuando) {
        Pago pago = TestData.cobrado(id, reserva, monto);
        pago.setProvider(medio);
        pago.setRegistradoPorId(empleado.getId());
        pago.setPaidAt(cuando);
        return pago;
    }

    private static DevolucionRequest devolucion(LineaDevolucionRequest... lineas) {
        DevolucionRequest req = new DevolucionRequest();
        req.setLineas(List.of(lineas));
        req.setMotivo("Se devolvió un producto");
        return req;
    }

    // ── Anular ───────────────────────────────────────────────────────────

    @Test
    void elEmpleadoAnulaUnEfectivoDeLaJornadaYLaReservaQuedaConfirmada() {
        Pago pago = cobro(1L, PaymentProvider.MANUAL, "7500", AHORA.minusMinutes(5));

        anulacion(AHORA).anular(10L, 1L, "  Lo cargué dos veces ", empleado.getEmail());

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.ANULADO);
        assertThat(pago.getMotivoAnulacion()).isEqualTo("Lo cargué dos veces");
        assertThat(pago.getAnuladoPor()).isSameAs(empleado);
        // la reserva ya estaba confirmada: no vuelve a PENDIENTE ni vence
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        assertThat(reserva.getMontoCubierto()).isEqualByComparingTo("0");
    }

    @Test
    void alas0030DelDiaSiguienteElEmpleadoSigueAnulandoLoDeLaJornada() {
        Pago pago = cobro(1L, PaymentProvider.CUENTA_DNI, "7500", HOY.atTime(22, 0));

        anulacion(HOY.plusDays(1).atTime(0, 30)).anular(10L, 1L, "Error de carga", empleado.getEmail());

        assertThat(pago.getEstado()).isEqualTo(EstadoPago.ANULADO);
    }

    @Test
    void fueraDeLaJornadaSoloAnulaElAdmin() {
        Pago pago = cobro(1L, PaymentProvider.MANUAL, "7500", HOY.minusDays(1).atTime(21, 0));

        assertThatThrownBy(() -> anulacion(AHORA).anular(10L, 1L, "Error", empleado.getEmail()))
                .isInstanceOf(PermisoDenegadoException.class)
                .hasMessage("Solo podés anular cobros registrados en la jornada de hoy.");
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);

        anulacion(AHORA).anular(10L, 1L, "Error", admin.getEmail());
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.ANULADO);
    }

    @Test
    void unaBonificacionLaAnulaSoloElAdmin() {
        Pago bonificacion = cobro(1L, PaymentProvider.BONIFICACION, "5000", AHORA);

        assertThatThrownBy(() -> anulacion(AHORA).anular(10L, 1L, "Error", empleado.getEmail()))
                .isInstanceOf(PermisoDenegadoException.class)
                .hasMessage("Solo el ADMIN puede anular una bonificación.");
        anulacion(AHORA).anular(10L, 1L, "No correspondía", admin.getEmail());
        assertThat(bonificacion.getEstado()).isEqualTo(EstadoPago.ANULADO);
    }

    @Test
    void unQrNoSeAnula() {
        Pago qr = cobro(1L, PaymentProvider.MERCADO_PAGO, "7500", AHORA);
        qr.setOrderId("ORD-1");

        assertThatThrownBy(() -> anulacion(AHORA).anular(10L, 1L, "Error", admin.getEmail()))
                .isInstanceOf(EstadoInvalidoException.class);
    }

    // ── Devolver y entregar ──────────────────────────────────────────────

    @Test
    void unaDevolucionParcialEnElClubQuedaPorEntregarYDespuesSeEntrega() {
        Pago pago = cobro(1L, PaymentProvider.MANUAL, "17000", AHORA.minusHours(1));

        DevolucionRequest paraDespues = devolucion(
                new LineaDevolucionRequest(ConceptoImputacion.PRODUCTO, 51L, new BigDecimal("2000")));
        paraDespues.setEntregada(false);
        devoluciones(AHORA).devolver(10L, 1L, paraDespues, admin.getEmail());

        Devolucion d = pago.getDevoluciones().getFirst();
        d.setId(70L);
        assertThat(d.getEstado()).isEqualTo(EstadoDevolucion.A_REEMBOLSAR);
        assertThat(d.getRegistradaPor()).isSameAs(admin);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
        assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA); // quedó debiendo el Gatorade

        // por entregar: el empleado la ve en la jornada y la entrega
        when(pagoRepository.findDevolucionesEnEstado(EstadoDevolucion.A_REEMBOLSAR)).thenReturn(List.of(d));
        PagoConsultaService consultas = new PagoConsultaService(pagoRepository, null, null, proveedorQr,
                new com.LosCiruelos.padel_club_api.mapper.PagoMapper(), usuarioService, TestData.jornadas(TestData.reloj(AHORA)));
        assertThat(consultas.devolucionesAEntregar(empleado.getEmail())).singleElement().satisfies(r -> {
            assertThat(r.devolucionId()).isEqualTo(70L);
            assertThat(r.monto()).isEqualByComparingTo("2000");
            assertThat(r.medio()).isEqualTo(com.LosCiruelos.padel_club_api.pagos.entity.enums.CanalDePago.EFECTIVO);
        });

        devoluciones(AHORA.plusMinutes(5)).entregar(10L, 70L, empleado.getEmail());
        assertThat(d.getEstado()).isEqualTo(EstadoDevolucion.DEVUELTO);
        assertThat(d.getEntregadoAt()).isEqualTo(AHORA.plusMinutes(5));
        assertThat(d.getEntregadoPor()).isSameAs(empleado);
    }

    @Test
    void unaDevolucionEnElClubSeEntregaEnElMomentoPorDefecto() {
        Pago pago = cobro(1L, PaymentProvider.CUENTA_DNI, "17000", AHORA.minusHours(1));

        devoluciones(AHORA).devolver(10L, 1L, devolucion(
                new LineaDevolucionRequest(ConceptoImputacion.CANCHA, null, new BigDecimal("1000"))), admin.getEmail());

        Devolucion d = pago.getDevoluciones().getFirst();
        assertThat(d.getEstado()).isEqualTo(EstadoDevolucion.DEVUELTO);
        assertThat(d.getEntregadoAt()).isEqualTo(AHORA);
        assertThat(d.getEntregadoPor()).isSameAs(admin);
        assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
    }

    @Test
    void alCancelarElPersonalLoDelClubSeDevuelveEnElMomentoOQuedaParaDespues() {
        Pago enElClub = cobro(1L, PaymentProvider.MANUAL, "10000", AHORA.minusDays(1));

        reembolsoService.reembolsar(reserva, new BigDecimal("10000"), empleado, true);
        assertThat(enElClub.getDevoluciones().getFirst().getEstado()).isEqualTo(EstadoDevolucion.DEVUELTO);
        assertThat(enElClub.getDevoluciones().getFirst().getEntregadoPor()).isSameAs(empleado);
        assertThat(enElClub.getEstado()).isEqualTo(EstadoPago.DEVUELTO);

        Pago otro = cobro(2L, PaymentProvider.MANUAL, "5000", AHORA.minusDays(1));
        reembolsoService.reembolsar(reserva, new BigDecimal("5000"), empleado, false);
        assertThat(otro.getDevoluciones().getFirst().getEstado()).isEqualTo(EstadoDevolucion.A_REEMBOLSAR);
        assertThat(otro.getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
    }

    @Test
    void elEmpleadoNoEntregaUnaDevolucionDeOtraJornada() {
        Pago pago = cobro(1L, PaymentProvider.MANUAL, "17000", AHORA.minusDays(2));
        Devolucion d = pago.devolver(List.of(new PagoImputacion.Pedido(ConceptoImputacion.CANCHA, null,
                new BigDecimal("1000"))), null, admin, AHORA.minusDays(2));
        d.setId(70L);

        assertThatThrownBy(() -> devoluciones(AHORA).entregar(10L, 70L, empleado.getEmail()))
                .isInstanceOf(PermisoDenegadoException.class)
                .hasMessage("Solo podés entregar devoluciones de la jornada de hoy.");
        devoluciones(AHORA).entregar(10L, 70L, admin.getEmail());
        assertThat(d.getEstado()).isEqualTo(EstadoDevolucion.DEVUELTO);
    }

    // ── El EMPLEADO devuelve, con revisión del ADMIN ─────────────────────

    @Test
    void elEmpleadoDevuelveUnCobroDeSuJornadaYQuedaSinRevisar() {
        Pago pago = cobro(1L, PaymentProvider.MANUAL, "17000", AHORA.minusHours(1));

        devoluciones(AHORA).devolver(10L, 1L, devolucion(
                new LineaDevolucionRequest(ConceptoImputacion.PRODUCTO, 51L, new BigDecimal("2000"))), empleado.getEmail());

        Devolucion d = pago.getDevoluciones().getFirst();
        assertThat(d.getRegistradaPor()).isSameAs(empleado);
        assertThat(d.isRevisada()).isFalse();
        assertThat(d.getRevisadaAt()).isNull();
        assertThat(d.getEstado()).isEqualTo(EstadoDevolucion.DEVUELTO); // entregada en el momento
    }

    @Test
    void alas0030ElEmpleadoSigueDevolviendoLoDeLaJornada() {
        Pago pago = cobro(1L, PaymentProvider.CUENTA_DNI, "17000", HOY.atTime(22, 0));

        devoluciones(HOY.plusDays(1).atTime(0, 30)).devolver(10L, 1L, devolucion(
                new LineaDevolucionRequest(ConceptoImputacion.CANCHA, null, new BigDecimal("1000"))), empleado.getEmail());

        assertThat(pago.getDevoluciones()).hasSize(1);
    }

    @Test
    void fueraDeLaJornadaElEmpleadoNoDevuelve() {
        Pago pago = cobro(1L, PaymentProvider.MANUAL, "17000", HOY.minusDays(1).atTime(21, 0));

        assertThatThrownBy(() -> devoluciones(AHORA).devolver(10L, 1L, devolucion(
                new LineaDevolucionRequest(ConceptoImputacion.CANCHA, null, BigDecimal.TEN)), empleado.getEmail()))
                .isInstanceOf(PermisoDenegadoException.class)
                .hasMessage("Solo podés devolver cobros registrados en la jornada de hoy.");
        assertThat(pago.getDevoluciones()).isEmpty();
    }

    @Test
    void elEmpleadoTieneQueIndicarElMotivo() {
        cobro(1L, PaymentProvider.MANUAL, "17000", AHORA.minusHours(1));
        DevolucionRequest sinMotivo = devolucion(
                new LineaDevolucionRequest(ConceptoImputacion.CANCHA, null, BigDecimal.TEN));
        sinMotivo.setMotivo("  ");

        assertThatThrownBy(() -> devoluciones(AHORA).devolver(10L, 1L, sinMotivo, empleado.getEmail()))
                .isInstanceOf(com.LosCiruelos.padel_club_api.common.exception.ReservaException.class)
                .hasMessage("Indicá el motivo de la devolución.");
        // al ADMIN no se le exige
        sinMotivo.setMotivo(null);
        devoluciones(AHORA).devolver(10L, 1L, sinMotivo, admin.getEmail());
    }

    @Test
    void lasDelAdminNacenRevisadasYElAdminRevisaLasDelEmpleado() {
        Pago pago = cobro(1L, PaymentProvider.MANUAL, "17000", AHORA.minusHours(1));
        devoluciones(AHORA).devolver(10L, 1L, devolucion(
                new LineaDevolucionRequest(ConceptoImputacion.CANCHA, null, new BigDecimal("1000"))), admin.getEmail());
        assertThat(pago.getDevoluciones().getFirst().isRevisada()).isTrue();
        assertThat(pago.getDevoluciones().getFirst().getRevisadaPor()).isSameAs(admin);

        devoluciones(AHORA).devolver(10L, 1L, devolucion(
                new LineaDevolucionRequest(ConceptoImputacion.CANCHA, null, new BigDecimal("1000"))), empleado.getEmail());
        Devolucion delEmpleado = pago.getDevoluciones().getLast();
        delEmpleado.setId(71L);

        when(pagoRepository.findDevolucionesSinRevisar()).thenReturn(List.of(delEmpleado));
        PagoConsultaService consultas = new PagoConsultaService(pagoRepository, null, null, proveedorQr,
                new com.LosCiruelos.padel_club_api.mapper.PagoMapper(), usuarioService, TestData.jornadas(TestData.reloj(AHORA)));
        assertThat(consultas.devolucionesSinRevisar(admin.getEmail())).singleElement().satisfies(r -> {
            assertThat(r.devolucionId()).isEqualTo(71L);
            assertThat(r.registradaPor()).isEqualTo(empleado.getNombre() + " " + empleado.getApellido());
            assertThat(r.revisada()).isFalse();
        });
        assertThatThrownBy(() -> consultas.devolucionesSinRevisar(empleado.getEmail()))
                .isInstanceOf(PermisoDenegadoException.class);

        devoluciones(AHORA.plusHours(2)).revisar(10L, 71L, true, admin.getEmail());
        assertThat(delEmpleado.isRevisada()).isTrue();
        assertThat(delEmpleado.getRevisadaAt()).isEqualTo(AHORA.plusHours(2));
        assertThat(delEmpleado.getRevisadaPor()).isSameAs(admin);

        devoluciones(AHORA).revisar(10L, 71L, false, admin.getEmail());
        assertThat(delEmpleado.isRevisada()).isFalse();
        assertThat(delEmpleado.getRevisadaPor()).isNull();
        assertThatThrownBy(() -> devoluciones(AHORA).revisar(10L, 71L, true, empleado.getEmail()))
                .isInstanceOf(PermisoDenegadoException.class);
    }

    @Test
    void unaDevolucionParcialOnlineLaHaceMercadoPagoEnElMomento() {
        Pago online = cobro(1L, PaymentProvider.MERCADO_PAGO, "17000", AHORA.minusDays(1));
        online.setExternalPaymentId("mp-1");
        online.setRegistradoPorId(null);

        devoluciones(AHORA).devolver(10L, 1L, devolucion(
                new LineaDevolucionRequest(ConceptoImputacion.PRODUCTO, 51L, new BigDecimal("2000"))), admin.getEmail());

        Devolucion d = online.getDevoluciones().getFirst();
        verify(mercadoPago).reembolsar(online, new BigDecimal("2000"), "devolucion-" + d.getClave());
        assertThat(d.getEstado()).isEqualTo(EstadoDevolucion.DEVUELTO);
        assertThat(d.getEntregadoAt()).isEqualTo(AHORA);
        assertThat(online.getMontoReembolsado()).isEqualByComparingTo("2000");
        assertThat(online.getEstado()).isEqualTo(EstadoPago.APROBADO);
    }

    @Test
    void siMercadoPagoNoAceptaLaDevolucionEs502() {
        Pago online = cobro(1L, PaymentProvider.MERCADO_PAGO, "17000", AHORA.minusDays(1));
        online.setExternalPaymentId("mp-1");
        doThrow(new ReembolsoFallidoException(false)).when(mercadoPago).reembolsar(eq(online), any(), any());

        assertThatThrownBy(() -> devoluciones(AHORA).devolver(10L, 1L, devolucion(
                new LineaDevolucionRequest(ConceptoImputacion.CANCHA, null, new BigDecimal("1000"))), admin.getEmail()))
                .isInstanceOf(PagoProveedorException.class)
                .hasMessage("Mercado Pago no aceptó la devolución. Intentá de nuevo.");
    }

    // ── Cancelación con reembolso, con el modelo nuevo ───────────────────

    @Test
    void alCancelarSeGeneranDevolucionesYSeLiberaLoQueCubria() {
        Pago enElClub = cobro(1L, PaymentProvider.MANUAL, "10000", AHORA.minusDays(1)); // cancha 10000
        Pago online = cobro(2L, PaymentProvider.MERCADO_PAGO, "7000", AHORA.minusDays(1)); // cancha 5000 + Gatorade 2000
        online.setExternalPaymentId("mp-2");

        reembolsoService.reembolsar(reserva, new BigDecimal("17000"));

        Devolucion porMp = online.getDevoluciones().getFirst();
        assertThat(porMp.getEstado()).isEqualTo(EstadoDevolucion.DEVUELTO);
        assertThat(porMp.getLineas()).extracting(DevolucionLinea::getNombre).containsExactly("Cancha", "Gatorade");
        assertThat(online.getEstado()).isEqualTo(EstadoPago.DEVUELTO);
        Devolucion aMano = enElClub.getDevoluciones().getFirst();
        assertThat(aMano.getEstado()).isEqualTo(EstadoDevolucion.A_REEMBOLSAR);
        assertThat(aMano.getMonto()).isEqualByComparingTo("10000");
        assertThat(enElClub.getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
        assertThat(reserva.getMontoCanchaPagado()).isEqualByComparingTo("0");
    }

    @Test
    void siMercadoPagoNoAceptaElReembolsoDeLaCancelacionLaDevolucionQuedaPendienteYElJobLaResuelve() {
        Pago online = cobro(1L, PaymentProvider.MERCADO_PAGO, "7500", AHORA.minusDays(1));
        online.setExternalPaymentId("mp-1");
        doThrow(new ReembolsoFallidoException(false)).doNothing().when(mercadoPago).reembolsar(online,
                new BigDecimal("7500"));
        when(pagoRepository.findReservaIdByPagoId(1L)).thenReturn(java.util.Optional.of(10L));
        when(mercadoPago.consultarPago("mp-1")).thenReturn(resultado(
                com.LosCiruelos.padel_club_api.pagos.payment.EstadoPayment.APROBADO, "7500", "mp-1", 1L));

        reembolsoService.reembolsar(reserva, new BigDecimal("7500"));
        assertThat(online.getDevoluciones().getFirst().getEstado()).isEqualTo(EstadoDevolucion.REEMBOLSO_PENDIENTE);

        reembolsoService.reintentarReembolso(1L);
        assertThat(online.getDevoluciones().getFirst().getEstado()).isEqualTo(EstadoDevolucion.DEVUELTO);
        assertThat(online.getEstado()).isEqualTo(EstadoPago.DEVUELTO);
    }
}
