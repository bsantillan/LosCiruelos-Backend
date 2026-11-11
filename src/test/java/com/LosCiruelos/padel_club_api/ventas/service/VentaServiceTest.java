package com.LosCiruelos.padel_club_api.ventas.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.club.service.ProductoService;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.VentaMapper;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.ventas.dto.request.VentaRequest;
import com.LosCiruelos.padel_club_api.ventas.dto.response.VentaResponse;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaRepository;

class VentaServiceTest {

    private VentaRepository ventaRepository;
    private ProductoService productoService;
    private UsuarioService usuarioService;
    private VentaService servicio;
    private CobroQrDeVentas cobroQr;

    private Usuario empleado;
    private Usuario cliente;
    private Producto agua;
    private Producto pelotas;
    private Producto paletas;

    @BeforeEach
    void setUp() {
        ventaRepository = mock(VentaRepository.class);
        productoService = mock(ProductoService.class);
        usuarioService = mock(UsuarioService.class);
        cobroQr = mock(CobroQrDeVentas.class);
        when(cobroQr.vencimiento()).thenReturn(AHORA.plusMinutes(10));
        servicio = new VentaService(ventaRepository, productoService, usuarioService, new VentaMapper(),
                TestData.reloj(AHORA), cobroQr);

        empleado = usuario(2L, Role.EMPLEADO);
        cliente = usuario(3L, Role.CLIENTE);
        when(usuarioService.autenticado(empleado.getEmail())).thenReturn(empleado);
        when(usuarioService.findByIdOrThrow(3L)).thenReturn(cliente);
        when(usuarioService.findByIdOrThrow(2L)).thenReturn(empleado);

        agua = Producto.builder().id(5L).nombre("Agua").tipo(TipoProducto.VENTA).precio(new BigDecimal("1500"))
                .stock(10).build();
        pelotas = Producto.builder().id(1L).nombre("Pelotas").tipo(TipoProducto.VENTA).precio(new BigDecimal("8000"))
                .stock(10).build();
        paletas = Producto.builder().id(2L).nombre("Paletas").tipo(TipoProducto.ALQUILER)
                .precio(new BigDecimal("2000")).stock(4).build();
        when(productoService.obtener(5L)).thenReturn(agua);
        when(productoService.obtener(1L)).thenReturn(pelotas);
        when(productoService.obtener(2L)).thenReturn(paletas);
        when(ventaRepository.save(any())).thenAnswer(inv -> {
            Venta v = inv.getArgument(0);
            v.setId(40L);
            return v;
        });
    }

    private static VentaRequest.Item item(Long productoId, int cantidad) {
        VentaRequest.Item item = new VentaRequest.Item();
        item.setProductoId(productoId);
        item.setCantidad(cantidad);
        return item;
    }

    private static VentaRequest pedido(MedioDePago medio, Long clienteId, VentaRequest.Item... items) {
        VentaRequest req = new VentaRequest();
        req.setMedio(medio);
        req.setClienteId(clienteId);
        req.setItems(List.of(items));
        return req;
    }

    @Test
    void registraLaVentaConElPrecioDeHoyYDescuentaElStock() {
        VentaResponse r = servicio.registrar(pedido(MedioDePago.EFECTIVO, 3L, item(5L, 2), item(1L, 1), item(5L, 1)),
                empleado.getEmail());

        assertThat(r.id()).isEqualTo(40L);
        assertThat(r.fecha()).isEqualTo(AHORA);
        assertThat(r.medio()).isEqualTo(MedioDePago.EFECTIVO);
        assertThat(r.total()).isEqualTo(new BigDecimal("12500.00"));
        assertThat(r.clienteId()).isEqualTo(3L);
        assertThat(r.registradaPor()).isEqualTo("Nombre2 Apellido2");
        // el agua repetida queda en un solo ítem
        assertThat(r.items()).containsExactly(
                new VentaResponse.Item(5L, "Agua", 3, new BigDecimal("1500.00"), new BigDecimal("4500.00"), 0),
                new VentaResponse.Item(1L, "Pelotas", 1, new BigDecimal("8000.00"), new BigDecimal("8000.00"), 0));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<Producto, Integer>> cantidades = ArgumentCaptor.forClass(Map.class);
        verify(productoService).descontarVentaSuelta(cantidades.capture());
        assertThat(cantidades.getValue()).containsExactlyInAnyOrderEntriesOf(Map.of(agua, 3, pelotas, 1));
    }

    @Test
    void sinClienteEsUnaVentaAlPublico() {
        VentaResponse r = servicio.registrar(pedido(MedioDePago.EFECTIVO, null, item(5L, 1)), empleado.getEmail());

        assertThat(r.clienteId()).isNull();
        assertThat(r.clienteNombre()).isNull();
        assertThat(r.estado()).isEqualTo(EstadoVenta.COBRADA);
        assertThat(r.qr()).isNull();
        verify(cobroQr, never()).ocuparCaja();
    }

    @Test
    void conMercadoPagoQuedaEsperandoElQrDeLaCaja() {
        doAnswer(inv -> {
            inv.<Venta>getArgument(0).setOrderId("ORD1");
            return null;
        }).when(cobroQr).asignar(any());

        VentaResponse r = servicio.registrar(pedido(MedioDePago.MERCADO_PAGO, null, item(5L, 1)), empleado.getEmail());

        assertThat(r.estado()).isEqualTo(EstadoVenta.ESPERANDO_PAGO);
        assertThat(r.qr()).isEqualTo(new VentaResponse.Qr("ORD1", AHORA.plusMinutes(10)));
        // primero la caja, después el stock, y la order con la venta ya guardada
        var orden = inOrder(cobroQr, productoService, ventaRepository);
        orden.verify(cobroQr).exigirConfigurado();
        orden.verify(cobroQr).ocuparCaja();
        orden.verify(productoService).descontarVentaSuelta(anyMap());
        orden.verify(ventaRepository).save(any());
        orden.verify(cobroQr).asignar(any());
    }

    @Test
    void sinElQrConfiguradoNoSeVendeConMercadoPago() {
        doThrow(new com.LosCiruelos.padel_club_api.pagos.exception.CobroQrNoConfiguradoException()).when(cobroQr)
                .exigirConfigurado();

        assertThatThrownBy(() -> servicio.registrar(pedido(MedioDePago.MERCADO_PAGO, null, item(5L, 1)),
                empleado.getEmail()))
                .hasMessage("El cobro con QR no está configurado.");
        verify(productoService, never()).descontarVentaSuelta(anyMap());
    }

    private Venta conQr(Long id) {
        Venta venta = Venta.builder().id(id).createdAt(AHORA).medio(MedioDePago.MERCADO_PAGO).registradoPor(empleado)
                .orderId("ORD1").build();
        venta.agregar(agua, 2);
        when(ventaRepository.findByIdForUpdate(id)).thenReturn(java.util.Optional.of(venta));
        return venta;
    }

    @Test
    void anularUnaVentaCobradaConQrDevuelveElTotalPorMercadoPago() {
        Venta venta = conQr(7L);

        servicio.anular(7L, "Error de carga", empleado.getEmail());

        verify(cobroQr).reembolsar(venta, new BigDecimal("3000"), "anulacion");
        verify(productoService).reponerVentaSuelta(Map.of(agua, 2));
    }

    @Test
    void siMercadoPagoNoDevuelveNoSeAnula() {
        conQr(7L);
        doThrow(new com.LosCiruelos.padel_club_api.ventas.exception.ReembolsoQrException()).when(cobroQr)
                .reembolsar(any(), any(), anyString());

        assertThatThrownBy(() -> servicio.anular(7L, "Error", empleado.getEmail()))
                .hasMessage("Mercado Pago no aceptó el reembolso. Intentá de nuevo.");
        verify(productoService, never()).reponerVentaSuelta(anyMap());
    }

    @Test
    void cadaDevolucionDeUnaVentaConQrSeReembolsaConSuPropiaClave() {
        Usuario admin = usuario(1L, Role.ADMIN);
        when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
        Venta venta = conQr(7L);

        servicio.devolver(7L, devolucion("Vencida", 5L, 1, true), admin.getEmail());
        servicio.devolver(7L, devolucion("Abierta", 5L, 1, false), admin.getEmail());

        verify(cobroQr).reembolsar(venta, new BigDecimal("1500"), "devolucion-1");
        verify(cobroQr).reembolsar(venta, new BigDecimal("1500"), "devolucion-2");
    }

    @Test
    void unProductoQueNoEstaCargadoSeDaDeAltaConSuNombreYPrecio() {
        Producto gatorade = Producto.builder().id(9L).nombre("Gatorade").tipo(TipoProducto.VENTA)
                .precio(new BigDecimal("2500")).stock(3).build();
        when(productoService.productoManual(org.mockito.ArgumentMatchers.anyString(), eq(new BigDecimal("2500")),
                eq(3))).thenReturn(gatorade);
        VentaRequest.Item manual = new VentaRequest.Item();
        manual.setNombre("Gatorade");
        manual.setPrecio(new BigDecimal("2500"));
        manual.setCantidad(2);
        // el mismo, escrito de nuevo: se da de alta con stock para las 3 unidades
        VentaRequest.Item otraVez = new VentaRequest.Item();
        otraVez.setNombre(" gatorade ");
        otraVez.setPrecio(new BigDecimal("2500"));
        otraVez.setCantidad(1);

        VentaResponse r = servicio.registrar(pedido(MedioDePago.EFECTIVO, null, manual, otraVez), empleado.getEmail());

        assertThat(r.total()).isEqualTo(new BigDecimal("7500.00"));
        assertThat(r.items()).extracting(VentaResponse.Item::productoId).containsExactly(9L);
        verify(productoService, org.mockito.Mockito.times(2)).productoManual(org.mockito.ArgumentMatchers.anyString(),
                eq(new BigDecimal("2500")), eq(3));
    }

    @Test
    void cadaItemLlevaElProductoOSuNombreYPrecio() {
        VentaRequest.Item sinPrecio = new VentaRequest.Item();
        sinPrecio.setNombre("Gatorade");
        sinPrecio.setCantidad(1);

        assertThatThrownBy(() -> servicio.registrar(pedido(MedioDePago.EFECTIVO, null, sinPrecio), empleado.getEmail()))
                .isInstanceOf(ReservaException.class)
                .hasMessage("Indicá el producto (productoId) o su nombre y precio.");
        verify(ventaRepository, never()).save(any());
    }

    @Test
    void unAlquilerNoSeVendeSuelto() {
        assertThatThrownBy(() -> servicio.registrar(pedido(MedioDePago.EFECTIVO, null, item(2L, 1)),
                empleado.getEmail()))
                .isInstanceOf(ReservaException.class)
                .hasMessage("Paletas se alquila con un turno: no se vende suelto.");
        verify(productoService, never()).descontarVentaSuelta(anyMap());
    }

    @Test
    void elClienteTieneQueSerUnCliente() {
        assertThatThrownBy(() -> servicio.registrar(pedido(MedioDePago.EFECTIVO, 2L, item(5L, 1)),
                empleado.getEmail()))
                .isInstanceOf(ReservaException.class)
                .hasMessage("El usuario indicado no es un cliente.");
    }

    @Test
    void unaVentaConCuentaDniQuedaSinVerificar() {
        VentaResponse r = servicio.registrar(pedido(MedioDePago.CUENTA_DNI, null, item(5L, 1)), empleado.getEmail());

        assertThat(r.verificada()).isFalse();
        assertThat(servicio.registrar(pedido(MedioDePago.EFECTIVO, null, item(5L, 1)), empleado.getEmail())
                .verificada()).isNull();
    }

    @Test
    void soloElAdminVerificaYSoloLasDeCuentaDni() {
        Usuario admin = usuario(1L, Role.ADMIN);
        when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
        Venta dni = Venta.builder().id(7L).createdAt(AHORA).medio(MedioDePago.CUENTA_DNI).registradoPor(empleado)
                .build();
        dni.agregar(agua, 1);
        Venta efectivo = Venta.builder().id(8L).createdAt(AHORA).medio(MedioDePago.EFECTIVO).registradoPor(empleado)
                .build();
        efectivo.agregar(agua, 1);
        when(ventaRepository.findByIdForUpdate(7L)).thenReturn(java.util.Optional.of(dni));
        when(ventaRepository.findByIdForUpdate(8L)).thenReturn(java.util.Optional.of(efectivo));

        VentaResponse r = servicio.verificar(7L, true, admin.getEmail());
        assertThat(r.verificada()).isTrue();
        assertThat(r.verificadaAt()).isEqualTo(AHORA);
        assertThat(dni.getVerificadoPorId()).isEqualTo(1L);
        assertThat(servicio.verificar(7L, false, admin.getEmail()).verificada()).isFalse();

        assertThatThrownBy(() -> servicio.verificar(7L, true, empleado.getEmail()))
                .isInstanceOf(com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException.class)
                .hasMessage("Solo el ADMIN puede verificar una venta con Cuenta DNI.");
        assertThatThrownBy(() -> servicio.verificar(8L, true, admin.getEmail()))
                .isInstanceOf(com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException.class)
                .hasMessage("Solo se verifican las ventas con Cuenta DNI.");
        when(ventaRepository.findByIdForUpdate(99L)).thenReturn(java.util.Optional.empty());
        assertThatThrownBy(() -> servicio.verificar(99L, true, admin.getEmail()))
                .isInstanceOf(com.LosCiruelos.padel_club_api.ventas.exception.VentaNoEncontradaException.class);
    }

    private Venta vendida(Long id, java.time.LocalDateTime cuando) {
        Venta venta = Venta.builder().id(id).createdAt(cuando).medio(MedioDePago.EFECTIVO).registradoPor(empleado)
                .build();
        venta.agregar(agua, 2);
        when(ventaRepository.findByIdForUpdate(id)).thenReturn(java.util.Optional.of(venta));
        return venta;
    }

    @Test
    void anularDevuelveElStockYGuardaQuienYPorQue() {
        vendida(7L, AHORA.minusHours(1));

        VentaResponse r = servicio.anular(7L, "  Error de carga ", empleado.getEmail());

        assertThat(r.estado()).isEqualTo(com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta.ANULADA);
        assertThat(r.anuladaPor()).isEqualTo("Nombre2 Apellido2");
        assertThat(r.anuladaAt()).isEqualTo(AHORA);
        assertThat(r.motivoAnulacion()).isEqualTo("Error de carga");
        verify(productoService).reponerVentaSuelta(Map.of(agua, 2));
    }

    @Test
    void elEmpleadoNoAnulaUnaVentaDeAyerPeroElAdminSi() {
        Usuario admin = usuario(1L, Role.ADMIN);
        when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
        vendida(7L, AHORA.minusDays(1));

        assertThatThrownBy(() -> servicio.anular(7L, "Error", empleado.getEmail()))
                .isInstanceOf(com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException.class)
                .hasMessage("Solo podés anular ventas de hoy.");
        verify(productoService, never()).reponerVentaSuelta(anyMap());

        assertThat(servicio.anular(7L, "Se cargó dos veces", admin.getEmail()).anuladaPor())
                .isEqualTo("Nombre1 Apellido1");
    }

    @Test
    void unaVentaAnuladaNoDevuelveElStockDosVeces() {
        vendida(7L, AHORA).anular(empleado, "Error", AHORA);

        assertThatThrownBy(() -> servicio.anular(7L, "Otra vez", empleado.getEmail()))
                .isInstanceOf(com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException.class);
        verify(productoService, never()).reponerVentaSuelta(anyMap());
    }

    @Test
    void cambiaElClienteAsociado() {
        Venta venta = vendida(7L, AHORA);

        assertThat(servicio.cambiarCliente(7L, 3L, empleado.getEmail()).clienteId()).isEqualTo(3L);
        assertThat(servicio.cambiarCliente(7L, null, empleado.getEmail()).clienteId()).isNull();
        assertThatThrownBy(() -> servicio.cambiarCliente(7L, 2L, empleado.getEmail()))
                .isInstanceOf(ReservaException.class)
                .hasMessage("El usuario indicado no es un cliente.");
        assertThat(venta.getCliente()).isNull();
    }

    private static com.LosCiruelos.padel_club_api.ventas.dto.request.DevolucionVentaRequest devolucion(String motivo,
            Long productoId, int cantidad, boolean vuelveAlStock) {
        var item = new com.LosCiruelos.padel_club_api.ventas.dto.request.DevolucionVentaRequest.Item();
        item.setProductoId(productoId);
        item.setCantidad(cantidad);
        item.setVuelveAlStock(vuelveAlStock);
        var req = new com.LosCiruelos.padel_club_api.ventas.dto.request.DevolucionVentaRequest();
        req.setMotivo(motivo);
        req.setItems(List.of(item));
        return req;
    }

    @Test
    void elAdminRegistraUnaDevolucionYVuelveAlStockSoloLoQueSirve() {
        Usuario admin = usuario(1L, Role.ADMIN);
        when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
        vendida(7L, AHORA.minusDays(3));

        VentaResponse r = servicio.devolver(7L, devolucion(" Vencida ", 5L, 1, true), admin.getEmail());

        assertThat(r.montoDevuelto()).isEqualTo(new BigDecimal("1500.00"));
        assertThat(r.items().get(0).devueltas()).isEqualTo(1);
        assertThat(r.devoluciones()).singleElement().satisfies(d -> {
            assertThat(d.fecha()).isEqualTo(AHORA);
            assertThat(d.registradaPor()).isEqualTo("Nombre1 Apellido1");
            assertThat(d.motivo()).isEqualTo("Vencida");
            assertThat(d.items()).containsExactly(new VentaResponse.ItemDevuelto(5L, "Agua", 1, true));
        });
        verify(productoService).reponerVentaSuelta(Map.of(agua, 1));

        // la otra, abierta: no vuelve al stock
        servicio.devolver(7L, devolucion("Abierta", 5L, 1, false), admin.getEmail());
        verify(productoService, org.mockito.Mockito.times(1)).reponerVentaSuelta(anyMap());
    }

    @Test
    void soloElAdminRegistraDevoluciones() {
        vendida(7L, AHORA);

        assertThatThrownBy(() -> servicio.devolver(7L, devolucion("Vencida", 5L, 1, true), empleado.getEmail()))
                .isInstanceOf(com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException.class)
                .hasMessage("Solo el ADMIN puede registrar una devolución.");
        verify(productoService, never()).reponerVentaSuelta(anyMap());
    }

    @Test
    void elEmpleadoSoloModificaLasVentasDeHoy() {
        vendida(7L, AHORA.minusDays(1));

        assertThatThrownBy(() -> servicio.cambiarCliente(7L, 3L, empleado.getEmail()))
                .isInstanceOf(com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException.class)
                .hasMessage("Solo podés modificar ventas de hoy.");
    }
}
