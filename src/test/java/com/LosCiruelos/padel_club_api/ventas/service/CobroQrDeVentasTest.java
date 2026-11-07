package com.LosCiruelos.padel_club_api.ventas.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.club.service.ProductoService;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.mapper.VentaMapper;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.exception.CobroQrNoConfiguradoException;
import com.LosCiruelos.padel_club_api.pagos.exception.ReembolsoFallidoException;
import com.LosCiruelos.padel_club_api.pagos.payment.OrdenQr;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.EstadoOrden;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.EstadoOrdenQr;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.OrdenNoCancelableException;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.pagos.service.CajaQrService;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;
import com.LosCiruelos.padel_club_api.ventas.exception.ReembolsoQrException;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaRepository;

/** El cobro de una venta suelta con el QR de la caja (CobroQrDeVentas y VentaQrService). */
class CobroQrDeVentasTest {

    private ProveedorCobroQr proveedor;
    private PagoRepository pagoRepository;
    private VentaRepository ventaRepository;
    private ProductoService productoService;
    private CobroQrDeVentas cobro;
    private VentaQrService ventaQr;

    private final Usuario empleado = usuario(2L, Role.EMPLEADO);
    private final Producto agua = Producto.builder().id(5L).nombre("Agua").tipo(TipoProducto.VENTA)
            .precio(new BigDecimal("1500")).stock(10).build();

    @BeforeEach
    void setUp() {
        proveedor = mock(ProveedorCobroQr.class);
        when(proveedor.estaConfigurado()).thenReturn(true);
        pagoRepository = mock(PagoRepository.class);
        ventaRepository = mock(VentaRepository.class);
        productoService = mock(ProductoService.class);
        // la caja real, con el repository de pagos mockeado
        cobro = new CobroQrDeVentas(proveedor, new CajaQrService(pagoRepository), ventaRepository, productoService,
                TestData.reloj(AHORA), 10);
        UsuarioService usuarioService = mock(UsuarioService.class);
        when(usuarioService.autenticado(empleado.getEmail())).thenReturn(empleado);
        ventaQr = new VentaQrService(ventaRepository, cobro, usuarioService, new VentaMapper(), TestData.reloj(AHORA));
    }

    private Venta esperando(Long id, LocalDateTime vence) {
        Venta venta = Venta.builder().id(id).createdAt(AHORA.minusMinutes(1)).medio(MedioDePago.MERCADO_PAGO)
                .registradoPor(empleado).orderId("ORD-" + id).build();
        venta.agregar(agua, 2);
        venta.esperarPagoConQr(vence);
        when(ventaRepository.findByIdForUpdate(id)).thenReturn(Optional.of(venta));
        when(ventaRepository.findIdByOrderId("ORD-" + id)).thenReturn(Optional.of(id));
        return venta;
    }

    private void laOrderQueda(String orderId, EstadoOrden estado, String pagado) {
        when(proveedor.consultar(orderId)).thenReturn(new EstadoOrdenQr(estado,
                pagado != null ? new BigDecimal(pagado) : null, "PAY1"));
    }

    @Nested
    class Caja {

        @Test
        void sinConfigurarNoSeOfrece() {
            when(proveedor.estaConfigurado()).thenReturn(false);

            assertThatThrownBy(cobro::exigirConfigurado).isInstanceOf(CobroQrNoConfiguradoException.class);
        }

        @Test
        void seBloqueaYEstaLibre() {
            cobro.ocuparCaja();

            verify(pagoRepository).bloquearCajaQr();
        }

        @Test
        void ocupadaPorUnaReserva() {
            Reserva reserva = TestData.reserva(20L, usuario(1L, Role.CLIENTE), EstadoReserva.PENDIENTE, "15000");
            Pago qr = Pago.builder().id(99L).reserva(reserva).monto(new BigDecimal("7500")).estado(EstadoPago.EN_PROCESO)
                    .provider(PaymentProvider.MERCADO_PAGO).venceAt(AHORA.plusMinutes(4)).orderId("ORD-R").build();
            when(pagoRepository.findQrEnCurso(EstadoPago.EN_PROCESO)).thenReturn(List.of(qr));

            assertThatThrownBy(cobro::ocuparCaja)
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("El QR de la caja está esperando el pago de $7.500 de la reserva #20 hasta las 10:04: "
                            + "esperá a que se pague o cancelalo.");
        }

        @Test
        void unaReservaConElQrVencidoNoLaOcupa() {
            Reserva reserva = TestData.reserva(20L, usuario(1L, Role.CLIENTE), EstadoReserva.PENDIENTE, "15000");
            Pago qr = Pago.builder().id(99L).reserva(reserva).monto(new BigDecimal("7500")).estado(EstadoPago.EN_PROCESO)
                    .provider(PaymentProvider.MERCADO_PAGO).venceAt(AHORA.minusMinutes(1)).orderId("ORD-R").build();
            when(pagoRepository.findQrEnCurso(EstadoPago.EN_PROCESO)).thenReturn(List.of(qr));

            cobro.ocuparCaja();
        }

        @Test
        void ocupadaPorOtraVenta() {
            Venta otra = esperando(8L, AHORA.plusMinutes(6));
            when(ventaRepository.findByEstado(EstadoVenta.ESPERANDO_PAGO)).thenReturn(List.of(otra));

            assertThatThrownBy(cobro::ocuparCaja)
                    .hasMessage("El QR de la caja está esperando el pago de $3.000 de la venta #8 hasta las 10:06: "
                            + "esperá a que se pague o cancelalo.");
            assertThat(ventaQr.ventaEsperando(AHORA)).isPresent();
        }

        @Test
        void otraVentaVencidaSeResuelveYLaCajaQuedaLibre() {
            Venta otra = esperando(8L, AHORA.minusMinutes(2));
            when(ventaRepository.findByEstado(EstadoVenta.ESPERANDO_PAGO)).thenReturn(List.of(otra));
            laOrderQueda("ORD-8", EstadoOrden.VENCIDA, null);

            cobro.ocuparCaja();

            assertThat(otra.getEstado()).isEqualTo(EstadoVenta.NO_COBRADA);
            verify(productoService).reponerVentaSuelta(Map.of(agua, 2));
            assertThat(ventaQr.ventaEsperando(AHORA)).isEmpty();
        }

        @Test
        void otraVentaVencidaQueMercadoPagoSigueEsperandoLaOcupa() {
            Venta otra = esperando(8L, AHORA.minusMinutes(2));
            when(ventaRepository.findByEstado(EstadoVenta.ESPERANDO_PAGO)).thenReturn(List.of(otra));
            laOrderQueda("ORD-8", EstadoOrden.CREADA, null);

            assertThatThrownBy(cobro::ocuparCaja).hasMessage(CobroQrDeVentas.CAJA_OCUPADA);
        }
    }

    @Test
    void laOrderLlevaLaReferenciaDeLaVentaYSuTotal() {
        Venta venta = esperando(40L, AHORA.plusMinutes(10));
        venta.setOrderId(null);
        when(proveedor.crear(any(), anyString(), anyInt())).thenReturn("ORD-NUEVA");

        cobro.asignar(venta);

        assertThat(venta.getOrderId()).isEqualTo("ORD-NUEVA");
        verify(proveedor).crear(new OrdenQr(null, "venta-40", venta.getClaveIdempotencia().toString(),
                new BigDecimal("3000")), "Los Ciruelos - venta #40", 10);
        assertThat(cobro.vencimiento()).isEqualTo(AHORA.plusMinutes(10));
    }

    @Nested
    class Resolver {

        @Test
        void cobradaPorElMontoExactoUnaSolaVez() {
            Venta venta = esperando(40L, AHORA.plusMinutes(5));
            laOrderQueda("ORD-40", EstadoOrden.PROCESADA, "3000.00");

            assertThat(ventaQr.notificacion("ORD-40")).isTrue();
            assertThat(venta.getEstado()).isEqualTo(EstadoVenta.COBRADA);
            // notificación repetida: ya no espera, no se consulta otra vez
            ventaQr.notificacion("ORD-40");
            verify(proveedor, org.mockito.Mockito.times(1)).consultar("ORD-40");
            verify(productoService, never()).reponerVentaSuelta(anyMap());
        }

        @Test
        void cobradaPorOtroMontoQuedaSinCobrarYVuelveElStock() {
            Venta venta = esperando(40L, AHORA.plusMinutes(5));
            laOrderQueda("ORD-40", EstadoOrden.PROCESADA, "300.00");

            ventaQr.notificacion("ORD-40");

            assertThat(venta.getEstado()).isEqualTo(EstadoVenta.NO_COBRADA);
            verify(productoService).reponerVentaSuelta(Map.of(agua, 2));
        }

        @Test
        void unaOrderQueNoEsDeUnaVenta() {
            when(ventaRepository.findIdByOrderId("ORD-X")).thenReturn(Optional.empty());

            assertThat(ventaQr.notificacion("ORD-X")).isFalse();
        }

        @Test
        void elJobRevisaLasVencidasConUnMinutoDeMargen() {
            when(ventaRepository.findIdsQrVencidas(EstadoVenta.ESPERANDO_PAGO, AHORA.minusSeconds(60)))
                    .thenReturn(List.of(40L));
            assertThat(ventaQr.idsQrVencidas()).containsExactly(40L);

            Venta venta = esperando(40L, AHORA.minusMinutes(2));
            laOrderQueda("ORD-40", EstadoOrden.VENCIDA, null);
            assertThat(ventaQr.revisar(40L)).isEqualTo(EstadoVenta.NO_COBRADA);
            assertThat(ventaQr.revisar(40L)).isEqualTo(EstadoVenta.NO_COBRADA);
            verify(proveedor, org.mockito.Mockito.times(1)).consultar("ORD-40");
            assertThat(venta.getEstado()).isEqualTo(EstadoVenta.NO_COBRADA);
            assertThat(ventaQr.revisar(41L)).isNull();
        }
    }

    @Nested
    class Cancelar {

        @Test
        void cancelaLaOrderYElStockVuelve() {
            Venta venta = esperando(40L, AHORA.plusMinutes(5));

            assertThat(ventaQr.cancelar(40L, empleado.getEmail()).estado()).isEqualTo(EstadoVenta.NO_COBRADA);

            verify(proveedor).cancelar(eq(new OrdenQr("ORD-40", "venta-40", venta.getClaveIdempotencia().toString(),
                    new BigDecimal("3000"))));
            verify(productoService).reponerVentaSuelta(Map.of(agua, 2));
        }

        @Test
        void siYaSePagoQuedaCobrada() {
            Venta venta = esperando(40L, AHORA.plusMinutes(5));
            doThrow(new OrdenNoCancelableException("order_already_processed")).when(proveedor).cancelar(any());
            laOrderQueda("ORD-40", EstadoOrden.PROCESADA, "3000.00");

            ventaQr.cancelar(40L, empleado.getEmail());

            assertThat(venta.getEstado()).isEqualTo(EstadoVenta.COBRADA);
        }

        @Test
        void unaVentaQueNoEsperaEs409() {
            Venta venta = esperando(40L, AHORA.plusMinutes(5));
            venta.confirmarCobro();

            assertThatThrownBy(() -> ventaQr.cancelar(40L, empleado.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("La venta no está esperando el pago con QR.");
        }
    }

    @Test
    void elReembolsoUsaUnaClavePorOperacionYSiFallaEs502() {
        Venta venta = esperando(40L, AHORA.plusMinutes(5));
        venta.confirmarCobro();

        cobro.reembolsar(venta, new BigDecimal("1500"), "devolucion-1");
        verify(proveedor).reembolsar(new OrdenQr("ORD-40", "venta-40", venta.getClaveIdempotencia() + "-devolucion-1",
                new BigDecimal("3000")), new BigDecimal("1500"));

        doThrow(new ReembolsoFallidoException(true)).when(proveedor).reembolsar(any(), any());
        assertThatThrownBy(() -> cobro.reembolsar(venta, new BigDecimal("3000"), "anulacion"))
                .isInstanceOf(ReembolsoQrException.class);
    }
}
