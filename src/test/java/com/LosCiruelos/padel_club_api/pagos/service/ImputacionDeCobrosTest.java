package com.LosCiruelos.padel_club_api.pagos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.pagos.dto.request.ImputacionRequest;
import com.LosCiruelos.padel_club_api.pagos.dto.response.ImputacionResponse;
import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.ConceptoImputacion;
import com.LosCiruelos.padel_club_api.pagos.payment.EstadoPayment;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.EstadoOrden;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.EstadoOrdenQr;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaProducto;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;

/** Qué cubre cada cobro. La reserva: cancha de $15.000 y un Gatorade de $2.000 (línea 51). */
class ImputacionDeCobrosTest extends PagosTestBase {

    private static final String ORDER = "ORD-IMPUTACION";
    private Reserva reserva;
    private com.LosCiruelos.padel_club_api.usuarios.entity.Usuario admin;

    @BeforeEach
    void unaReservaConUnProducto() {
        admin = com.LosCiruelos.padel_club_api.TestData.usuario(3L,
                com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role.ADMIN);
        when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
        reserva = reservaBloqueada(EstadoReserva.SEÑADA);
        Producto gatorade = Producto.builder().id(5L).nombre("Gatorade").tipo(TipoProducto.VENTA)
                .precio(new BigDecimal("2000")).stock(10).build();
        reserva.getProductos().add(ReservaProducto.builder().id(51L).reserva(reserva).producto(gatorade).cantidad(1)
                .precioUnitario(new BigDecimal("2000")).build());
        reserva.setMontoTotal(new BigDecimal("17000"));
        reserva.setEstado(EstadoReserva.PENDIENTE);
    }

    private static ImputacionRequest imputacion(ConceptoImputacion concepto, Long lineaId, String monto) {
        return new ImputacionRequest(concepto, lineaId, new BigDecimal(monto));
    }

    @Test
    void unCobroConImputacionesGuardaLoQueCubre() {
        PagoResponse r = cobroService.registrarPagoManual(10L, new BigDecimal("9500"), PaymentProvider.MANUAL,
                List.of(imputacion(ConceptoImputacion.CANCHA, null, "7500"),
                        imputacion(ConceptoImputacion.PRODUCTO, 51L, "2000")), empleado.getEmail());

        assertThat(r.getImputaciones()).containsExactly(
                new ImputacionResponse(ConceptoImputacion.CANCHA, null, "Cancha", new BigDecimal("7500.00")),
                new ImputacionResponse(ConceptoImputacion.PRODUCTO, 51L, "Gatorade", new BigDecimal("2000.00")));
        assertThat(reserva.getMontoCanchaPendiente()).isEqualByComparingTo("7500");
        assertThat(reserva.pendienteDe(reserva.getProductos().getFirst())).isEqualByComparingTo("0");
    }

    @Test
    void unCobroConImputacionesQueNoCierranNoSeRegistra() {
        assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("9500"), PaymentProvider.MANUAL,
                List.of(imputacion(ConceptoImputacion.PRODUCTO, 51L, "9500")), empleado.getEmail()))
                .isInstanceOf(ReservaException.class)
                .hasMessageContaining("supera lo pendiente");
        verify(pagoRepository, never()).save(any());
        assertThat(reserva.getPagos()).isEmpty();
    }

    @Test
    void sinImputacionesElCobroCubrePrimeroLaCancha() {
        PagoResponse r = cobroService.registrarPagoManual(10L, new BigDecimal("16000"), PaymentProvider.CUENTA_DNI,
                null, empleado.getEmail());

        assertThat(r.getImputaciones()).extracting(ImputacionResponse::nombre, ImputacionResponse::monto)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Cancha", new BigDecimal("15000.00")),
                        org.assertj.core.groups.Tuple.tuple("Gatorade", new BigDecimal("1000.00")));
    }

    @Test
    void laBonificacionVaALaCanchaSalvoQueSeIndiqueOtraCosa() {
        cobroService.bonificar(10L, new BigDecimal("5000"), "socio", null, admin.getEmail());
        assertThat(reserva.getMontoCanchaPagado()).isEqualByComparingTo("5000");

        // sin monto, bonifica lo que suman las imputaciones
        PagoResponse r = cobroService.bonificar(10L, null, "cortesía",
                List.of(imputacion(ConceptoImputacion.PRODUCTO, 51L, "2000")), admin.getEmail());
        assertThat(r.getMonto()).isEqualByComparingTo("2000");
        assertThat(reserva.pagadoDe(reserva.getProductos().getFirst())).isEqualByComparingTo("2000");
    }

    @Test
    void elPagoOnlineDelClienteSeImputaAlAprobarse() {
        Pago pago = com.LosCiruelos.padel_club_api.TestData.pago(1L, reserva,
                com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago.PENDIENTE, "8500");

        pagoOnlineService.aplicarResultado(reserva, pago, resultado(EstadoPayment.APROBADO, "8500", "mp-1", 1L));

        assertThat(pago.getImputaciones()).singleElement()
                .satisfies(i -> assertThat(i.getMonto()).isEqualByComparingTo("8500"));
        assertThat(reserva.getMontoCanchaPagado()).isEqualByComparingTo("8500");
    }

    @Test
    void elQrGuardaLasImputacionesAlGenerarseYLasAplicaAlCobrarse() {
        when(proveedorQr.crear(any(), anyString(), anyInt())).thenReturn(ORDER);
        when(pagoRepository.findReservaIdByOrderId(ORDER)).thenReturn(Optional.of(10L));

        cobroQrService.generar(10L, null, List.of(imputacion(ConceptoImputacion.PRODUCTO, 51L, "2000")),
                empleado.getEmail());
        Pago qr = reserva.getPagos().stream().filter(Pago::esCobroQr).findFirst().orElseThrow();
        assertThat(qr.getMonto()).isEqualByComparingTo("2000");
        assertThat(qr.getImputaciones()).hasSize(1);
        assertThat(reserva.pagadoDe(reserva.getProductos().getFirst())).isEqualByComparingTo("0");

        when(proveedorQr.consultar(ORDER)).thenReturn(new EstadoOrdenQr(EstadoOrden.PROCESADA, new BigDecimal("2000"),
                "PAY-1"));
        cobroQrService.notificacion(ORDER);

        assertThat(reserva.pagadoDe(reserva.getProductos().getFirst())).isEqualByComparingTo("2000");
        assertThat(reserva.getMontoCanchaPagado()).isEqualByComparingTo("0");
    }

    @Test
    void siMercadoPagoCobraOtroMontoConElQrNoSeImputaNada() {
        when(proveedorQr.crear(any(), anyString(), anyInt())).thenReturn(ORDER);
        when(pagoRepository.findReservaIdByOrderId(ORDER)).thenReturn(Optional.of(10L));
        cobroQrService.generar(10L, new BigDecimal("7500"), null, empleado.getEmail());

        when(proveedorQr.consultar(ORDER)).thenReturn(new EstadoOrdenQr(EstadoOrden.PROCESADA, new BigDecimal("750"),
                "PAY-1"));
        cobroQrService.notificacion(ORDER);

        Pago qr = reserva.getPagos().stream().filter(Pago::esCobroQr).findFirst().orElseThrow();
        assertThat(qr.getImputaciones()).isEmpty();
        assertThat(reserva.getMontoCanchaPagado()).isEqualByComparingTo("0");
    }
}
