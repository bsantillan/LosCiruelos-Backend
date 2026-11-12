package com.LosCiruelos.padel_club_api.reservas.service;

import com.LosCiruelos.padel_club_api.mapper.ReservaMapper;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.mapper.ResumenMapper;
import com.LosCiruelos.padel_club_api.TestData;

import com.LosCiruelos.padel_club_api.reservas.repository.CobrosDelPeriodo;
import com.LosCiruelos.padel_club_api.reservas.repository.EntradasDeCaja;
import com.LosCiruelos.padel_club_api.reservas.repository.ProductoVendido;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnosDelPeriodo;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.ventas.repository.TotalesDeVentas;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaPorProducto;
import com.LosCiruelos.padel_club_api.ventas.service.VentaConsultaService;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;

/** Cómo arma el servicio la respuesta. Las consultas en sí se prueban en ReservaRepositoryResumenTest. */
class ReservaConsultaServiceResumenTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 10, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 10, 31);

    private static final String ADMIN = "admin@test.com";
    private static final String EMPLEADO = "emp@test.com";

    private ReservaRepository repo;
    private UsuarioService usuarioService;
    private VentaConsultaService ventas;
    private ReservaConsultaService resumen;

    @BeforeEach
    void setUp() {
        repo = mock(ReservaRepository.class);
        usuarioService = mock(UsuarioService.class);
        when(usuarioService.autenticado(ADMIN)).thenReturn(usuario(1L, Role.ADMIN));
        when(usuarioService.autenticado(EMPLEADO)).thenReturn(usuario(2L, Role.EMPLEADO));
        ventas = mock(VentaConsultaService.class);
        when(ventas.totales(any(), any())).thenReturn(new TotalesDeVentas(0L, $("0"), $("0"), $("0"), $("0")));
        when(ventas.devoluciones(any(), any())).thenReturn($("0"));
        resumen = new ReservaConsultaService(usuarioService, repo, mock(ReservaMapper.class), new ResumenMapper(),
                ventas, TestData.reloj(AHORA), com.LosCiruelos.padel_club_api.TestData.jornadas(com.LosCiruelos.padel_club_api.TestData.reloj(com.LosCiruelos.padel_club_api.TestData.AHORA)));
        // sin reservas ni pagos: las cantidades vienen en null y los montos en 0
        when(repo.resumenTurnos(any(), any(), any(), any(), any(), any()))
                .thenReturn(new TurnosDelPeriodo(null, $("0"), null, $("0"), null));
        when(repo.resumenCobros(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new CobrosDelPeriodo($("0"), $("0"), $("0"), $("0"), $("0")));
        when(repo.resumenEntradasDeCaja(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EntradasDeCaja($("0"), $("0"), $("0"), $("0"), $("0")));
        when(repo.resumenDevoluciones(any(), any(), any(), any())).thenReturn($("0"));
    }

    private static BigDecimal $(String monto) {
        return new BigDecimal(monto);
    }

    @Test
    void armaElResumenDelPeriodo() {
        when(repo.resumenTurnos(DESDE, HASTA, AHORA.toLocalDate(), AHORA.toLocalTime(), Reserva.ESTADOS_QUE_SE_JUEGAN,
                EstadoReserva.CANCELADA)).thenReturn(new TurnosDelPeriodo(28L, $("420000"), 6L, $("90000"), 3L));
        when(repo.resumenCobros(eq(DESDE), eq(HASTA), eq(AHORA.toLocalDate()), eq(AHORA.toLocalTime()),
                eq(Reserva.ESTADOS_QUE_SE_JUEGAN), eq(EstadoReserva.CANCELADA), eq(EstadoPago.APROBADO), any(),
                eq(PaymentProvider.BONIFICACION)))
                .thenReturn(new CobrosDelPeriodo($("380000"), $("15000"), $("45000"), $("0"), $("7500")));
        when(repo.resumenEntradasDeCaja(DESDE.atStartOfDay(), HASTA.plusDays(1).atStartOfDay(),
                Set.of(EstadoPago.APROBADO, EstadoPago.DEVUELTO, EstadoPago.A_REEMBOLSAR,
                        EstadoPago.REEMBOLSO_PENDIENTE),
                PaymentProvider.MANUAL, PaymentProvider.MERCADO_PAGO, PaymentProvider.TRANSFERENCIA_MP,
                PaymentProvider.CUENTA_DNI, PaymentProvider.BONIFICACION))
                .thenReturn(new EntradasDeCaja($("90000"), $("60000"), $("25000"), $("15000"), $("40000")));
        when(repo.resumenDevoluciones(DESDE.atStartOfDay(), HASTA.plusDays(1).atStartOfDay(), EstadoPago.DEVUELTO,
                PaymentProvider.BONIFICACION)).thenReturn($("7500"));
        ProductoVendido pelotas = new ProductoVendido(1L, "Pelotas", TipoProducto.VENTA, 40L, $("200000"));
        when(repo.resumenProductos(DESDE, HASTA, Reserva.ESTADOS_QUE_SE_JUEGAN)).thenReturn(List.of(pelotas));

        ResumenResponse r = resumen.resumen(DESDE, HASTA);

        assertThat(r.desde()).isEqualTo(DESDE);
        assertThat(r.hasta()).isEqualTo(HASTA);
        // deuda = total - cobrado - bonificado
        assertThat(r.jugados()).isEqualTo(new ResumenResponse.Jugados(28, $("420000.00"), $("380000.00"),
                $("15000.00"), $("25000.00")));
        assertThat(r.porJugar()).isEqualTo(new ResumenResponse.PorJugar(6, $("90000.00"), $("45000.00"),
                $("0.00"), $("45000.00")));
        assertThat(r.caja()).usingRecursiveComparison().ignoringFields("detalleMercadoPago").isEqualTo(new ResumenResponse.Caja($("230000.00"), $("90000.00"), $("100000.00"),
                $("40000.00"), $("7500.00"), $("222500.00"), null));
        assertThat(r.cancelaciones()).isEqualTo(new ResumenResponse.Cancelaciones(3, $("7500.00")));
        assertThat(r.productos()).containsExactly(pelotas);
        // Mercado Pago suma todo, con el detalle: online 60.000, QR 25.000 y transferencia 15.000
        assertThat(r.caja().detalleMercadoPago()).isEqualTo(new com.LosCiruelos.padel_club_api.pagos.dto.response
                .DetalleMercadoPago($("100000.00"), $("25000.00"), $("60000.00"), $("15000.00")));
    }

    @Test
    void sumaLasVentasSueltasALaCajaYJuntaLosProductosVendidos() {
        when(repo.resumenEntradasDeCaja(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new EntradasDeCaja($("90000"), $("60000"), $("25000"), $("15000"), $("40000")));
        when(repo.resumenProductos(DESDE, HASTA, Reserva.ESTADOS_QUE_SE_JUEGAN)).thenReturn(List.of(
                new ProductoVendido(1L, "Pelotas", TipoProducto.VENTA, 40L, $("200000"))));
        when(ventas.totales(DESDE.atStartOfDay(), HASTA.plusDays(1).atStartOfDay()))
                .thenReturn(new TotalesDeVentas(3L, $("6000"), $("5000"), $("3000"), $("1000")));
        when(ventas.devoluciones(DESDE.atStartOfDay(), HASTA.plusDays(1).atStartOfDay())).thenReturn($("2000"));
        when(ventas.porProducto(DESDE.atStartOfDay(), HASTA.plusDays(1).atStartOfDay())).thenReturn(List.of(
                new VentaPorProducto(1L, "Pelotas", TipoProducto.VENTA, 1L, $("5000")),
                new VentaPorProducto(7L, "Gatorade", TipoProducto.VENTA, 4L, $("10000"))));

        ResumenResponse r = resumen.resumen(DESDE, HASTA);

        assertThat(r.ventas()).isEqualTo(new ResumenResponse.Ventas(3, $("15000.00"), $("2000.00")));
        // efectivo 90.000 + 6.000; Mercado Pago 100.000 + 8.000; Cuenta DNI 40.000 + 1.000; se devolvieron 2.000
        assertThat(r.caja()).usingRecursiveComparison().ignoringFields("detalleMercadoPago").isEqualTo(new ResumenResponse.Caja($("245000.00"), $("96000.00"), $("108000.00"),
                $("41000.00"), $("2000.00"), $("243000.00"), null));
        // las ventas suman su QR (5.000) y su transferencia (3.000); no se pagan online
        assertThat(r.caja().detalleMercadoPago()).isEqualTo(new com.LosCiruelos.padel_club_api.pagos.dto.response
                .DetalleMercadoPago($("108000.00"), $("30000.00"), $("60000.00"), $("18000.00")));
        assertThat(r.productos()).containsExactly(
                new ProductoVendido(1L, "Pelotas", TipoProducto.VENTA, 41L, $("205000")),
                new ProductoVendido(7L, "Gatorade", TipoProducto.VENTA, 4L, $("10000")));
    }

    @Test
    void elRetenidoCuentaLoQueNoSeDevolvioDeLosPagosDevueltosOPorDevolver() {
        resumen.resumen(DESDE, HASTA);

        verify(repo).resumenCobros(DESDE, HASTA, AHORA.toLocalDate(), AHORA.toLocalTime(), Reserva.ESTADOS_QUE_SE_JUEGAN,
                EstadoReserva.CANCELADA, EstadoPago.APROBADO,
                Set.of(EstadoPago.DEVUELTO, EstadoPago.A_REEMBOLSAR, EstadoPago.REEMBOLSO_PENDIENTE),
                PaymentProvider.BONIFICACION);
    }

    @Test
    void unPeriodoSinReservasDaTodoEnCero() {
        ResumenResponse r = resumen.resumen(DESDE, DESDE);

        assertThat(r.jugados()).isEqualTo(new ResumenResponse.Jugados(0, $("0.00"), $("0.00"), $("0.00"),
                $("0.00")));
        assertThat(r.porJugar().turnos()).isZero();
        assertThat(r.caja().neto()).isEqualTo($("0.00"));
        assertThat(r.cancelaciones().turnos()).isZero();
        assertThat(r.productos()).isEmpty();
    }

    @Test
    void sinFechasEsElDiaDeHoyYLaCajaVaDeLas0A_Las24() {
        LocalDate hoy = AHORA.toLocalDate();
        ResumenResponse r = resumen.resumen(null, null, EMPLEADO);

        assertThat(r.desde()).isEqualTo(hoy);
        assertThat(r.hasta()).isEqualTo(hoy);
        verify(repo).resumenTurnos(hoy, hoy, hoy, AHORA.toLocalTime(), Reserva.ESTADOS_QUE_SE_JUEGAN,
                EstadoReserva.CANCELADA);
        verify(repo).resumenDevoluciones(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay(), EstadoPago.DEVUELTO,
                PaymentProvider.BONIFICACION);
    }

    @Test
    void elEmpleadoSoloVeElDiaDeHoy() {
        LocalDate hoy = AHORA.toLocalDate();
        assertThat(resumen.resumen(hoy, hoy, EMPLEADO).desde()).isEqualTo(hoy);

        assertThatThrownBy(() -> resumen.resumen(DESDE, HASTA, EMPLEADO))
                .isInstanceOf(PermisoDenegadoException.class)
                .hasMessage("Solo podés ver el resumen de hoy.");
        assertThatThrownBy(() -> resumen.resumen(hoy.minusDays(1), hoy, EMPLEADO))
                .isInstanceOf(PermisoDenegadoException.class);
        assertThatThrownBy(() -> resumen.resumen(hoy, hoy.plusDays(1), EMPLEADO))
                .isInstanceOf(PermisoDenegadoException.class);
    }

    @Test
    void elAdminVeCualquierPeriodo() {
        assertThat(resumen.resumen(DESDE, HASTA, ADMIN).desde()).isEqualTo(DESDE);
    }

    @Test
    void conUnaSolaFechaEsUnError() {
        assertThatThrownBy(() -> resumen.resumen(DESDE, null, ADMIN))
                .isInstanceOf(ReservaException.class)
                .hasMessageContaining("las dos fechas");
        assertThatThrownBy(() -> resumen.resumen(null, HASTA, EMPLEADO)).isInstanceOf(ReservaException.class);
    }

    @Test
    void validaElPeriodo() {
        ReservaRepository sinUso = mock(ReservaRepository.class);
        ReservaConsultaService servicio = new ReservaConsultaService(usuarioService, sinUso, mock(ReservaMapper.class),
                new ResumenMapper(), mock(VentaConsultaService.class), TestData.reloj(AHORA), com.LosCiruelos.padel_club_api.TestData.jornadas(com.LosCiruelos.padel_club_api.TestData.reloj(com.LosCiruelos.padel_club_api.TestData.AHORA)));
        assertThatThrownBy(() -> servicio.resumen(HASTA, DESDE))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La fecha desde no puede ser posterior a hasta.");
        assertThatThrownBy(() -> servicio.resumen(DESDE, DESDE.plusDays(366)))
                .isInstanceOf(ReservaException.class)
                .hasMessageContaining("366 días");
        verifyNoInteractions(sinUso);

        // un año bisiesto completo (366 días) se acepta
        resumen.resumen(LocalDate.of(2028, 1, 1), LocalDate.of(2028, 12, 31));
    }
}
