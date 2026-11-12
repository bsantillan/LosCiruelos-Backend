package com.LosCiruelos.padel_club_api.reservas.entity;

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
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.PagoImputacion;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.ConceptoImputacion;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.exception.LineaCobradaException;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

/** Qué cubre cada pago: cancha de $15.000, 2 Gatorade de $2.000 (línea 51) y un agua de $1.500 (línea 52). */
class ImputacionDePagosTest {

    private Reserva reserva;
    private ReservaProducto gatorade;
    private ReservaProducto agua;

    @BeforeEach
    void unaReservaConProductos() {
        reserva = reserva(10L, usuario(1L, Role.CLIENTE), EstadoReserva.SEÑADA, "20500");
        gatorade = linea(51L, "Gatorade", 2, "2000");
        agua = linea(52L, "Agua", 1, "1500");
    }

    private ReservaProducto linea(Long id, String nombre, int cantidad, String precio) {
        Producto producto = Producto.builder().id(id + 100).nombre(nombre).tipo(TipoProducto.VENTA)
                .precio(new BigDecimal(precio)).stock(10).build();
        ReservaProducto linea = ReservaProducto.builder().id(id).reserva(reserva).producto(producto)
                .cantidad(cantidad).precioUnitario(new BigDecimal(precio)).build();
        reserva.getProductos().add(linea);
        return linea;
    }

    private static PagoImputacion.Pedido cancha(String monto) {
        return new PagoImputacion.Pedido(ConceptoImputacion.CANCHA, null, new BigDecimal(monto));
    }

    private static PagoImputacion.Pedido producto(Long lineaId, String monto) {
        return new PagoImputacion.Pedido(ConceptoImputacion.PRODUCTO, lineaId, new BigDecimal(monto));
    }

    @Test
    void laAutomaticaCompletaPrimeroLaCanchaYDespuesLosProductosEnOrden() {
        List<PagoImputacion> partes = reserva.imputacionAutomatica(new BigDecimal("18000"));

        assertThat(partes).extracting(PagoImputacion::nombre, p -> p.getMonto().toPlainString())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Cancha", "15000"),
                        org.assertj.core.groups.Tuple.tuple("Gatorade", "3000"));
    }

    @Test
    void laAutomaticaSigueDondeQuedoElPagoAnterior() {
        TestData.cobrado(1L, reserva, "16000"); // cancha entera y $1.000 del Gatorade

        List<PagoImputacion> partes = reserva.imputacionAutomatica(new BigDecimal("4500"));

        assertThat(partes).extracting(PagoImputacion::nombre, p -> p.getMonto().toPlainString())
                .containsExactly(org.assertj.core.groups.Tuple.tuple("Gatorade", "3000"),
                        org.assertj.core.groups.Tuple.tuple("Agua", "1500"));
        assertThat(reserva.getMontoCanchaPendiente()).isEqualByComparingTo("0");
        assertThat(reserva.pendienteDe(gatorade)).isEqualByComparingTo("3000");
    }

    @Test
    void unaImputacionManualValidaCubreLoQueDiceElPersonal() {
        List<PagoImputacion> partes = reserva.imputacionPedida(List.of(producto(52L, "1500"), cancha("5000")),
                new BigDecimal("6500"));
        Pago pago = TestData.pago(2L, reserva, EstadoPago.APROBADO, "6500");
        pago.imputar(partes);

        assertThat(reserva.pagadoDe(agua)).isEqualByComparingTo("1500");
        assertThat(reserva.pendienteDe(agua)).isEqualByComparingTo("0");
        assertThat(reserva.getMontoCanchaPagado()).isEqualByComparingTo("5000");
        assertThat(reserva.pagadoDe(gatorade)).isEqualByComparingTo("0");
    }

    @Test
    void sinImputacionesSeImputaSola() {
        assertThat(reserva.imputacionPedida(null, new BigDecimal("7500")))
                .singleElement().satisfies(p -> assertThat(p.esDeLaCancha()).isTrue());
    }

    @Test
    void lasImputacionesManualesInvalidasDan400() {
        BigDecimal monto = new BigDecimal("5000");
        assertThatThrownBy(() -> reserva.imputacionPedida(List.of(cancha("4000")), monto))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La suma de las imputaciones ($4.000) tiene que ser igual al monto ($5.000).");
        assertThatThrownBy(() -> reserva.imputacionPedida(List.of(producto(51L, "5000")), monto))
                .hasMessage("Lo imputado a Gatorade ($5.000) supera lo pendiente de esa línea ($4.000).");
        assertThatThrownBy(() -> reserva.imputacionPedida(List.of(producto(99L, "5000")), monto))
                .hasMessage("La línea #99 no es de esta reserva.");
        assertThatThrownBy(() -> reserva.imputacionPedida(List.of(producto(51L, "2500"), producto(51L, "2500")),
                monto)).hasMessage("La línea #51 está repetida en las imputaciones.");
        assertThatThrownBy(() -> reserva.imputacionPedida(List.of(producto(null, "5000")), monto))
                .hasMessage("Indicá la línea (lineaId) de cada imputación a un producto.");
        assertThatThrownBy(() -> reserva.imputacionPedida(List.of(cancha("6000"), producto(51L, "-1000")), monto))
                .hasMessage("El monto de cada imputación tiene que ser mayor a cero.");
        assertThatThrownBy(() -> reserva.imputacionPedida(
                List.of(new PagoImputacion.Pedido(ConceptoImputacion.CANCHA, 51L, monto)), monto))
                .hasMessage("Una imputación a la cancha no lleva lineaId.");
    }

    @Test
    void noSeImputaMasQueLoPendienteDeLaCancha() {
        TestData.cobrado(1L, reserva, "10000");

        assertThatThrownBy(() -> reserva.imputacionPedida(List.of(cancha("6000")), new BigDecimal("6000")))
                .hasMessage("Lo imputado a la cancha ($6.000) supera lo pendiente de esa línea ($5.000).");
    }

    @Test
    void unPagoQueNoSeCobroOSeDevolvioEnteroNoCubreNada() {
        Pago pago = TestData.cobrado(1L, reserva, "15000");
        assertThat(reserva.getMontoCanchaPagado()).isEqualByComparingTo("15000");

        pago.setMontoReembolsado(pago.getMonto());
        pago.cambiarEstado(EstadoPago.DEVUELTO, AHORA);

        assertThat(pago.getImputaciones()).isEmpty();
        assertThat(reserva.getMontoCanchaPagado()).isEqualByComparingTo("0");
    }

    @Test
    void lasDeUnQrEsperandoElPagoTodaviaNoCuentan() {
        Pago qr = TestData.pago(1L, reserva, EstadoPago.EN_PROCESO, "15000");
        qr.imputar(reserva.imputacionAutomatica(qr.getMonto()));

        assertThat(reserva.getMontoCanchaPagado()).isEqualByComparingTo("0");
        qr.cambiarEstado(EstadoPago.APROBADO, AHORA);
        assertThat(reserva.getMontoCanchaPagado()).isEqualByComparingTo("15000");
    }

    @Test
    void unaLineaCobradaNoSeSacaNiSeBajaYLaQueNoTieneNadaSi() {
        TestData.cobrado(1L, reserva, "17000"); // cancha y $2.000 del Gatorade

        reserva.getProductos().remove(agua); // sin nada cobrado: se saca
        reserva.validarQueNoSacaLoCobrado(new BigDecimal("15000"));

        gatorade.setCantidad(1); // queda en $2.000: alcanza para lo cobrado
        reserva.validarQueNoSacaLoCobrado(new BigDecimal("15000"));

        reserva.getProductos().remove(gatorade);
        assertThatThrownBy(() -> reserva.validarQueNoSacaLoCobrado(new BigDecimal("15000")))
                .isInstanceOf(LineaCobradaException.class)
                .hasMessage("Gatorade tiene $2.000 cobrados: anulá o devolvé ese cobro antes de sacarlo.")
                .satisfies(e -> assertThat(((LineaCobradaException) e).getDatos())
                        .containsEntry("lineaId", 51L)
                        .containsEntry("montoCobrado", new BigDecimal("2000.00")));
    }

    @Test
    void laCanchaNoBajaDeLoQueTieneCobrado() {
        TestData.cobrado(1L, reserva, "15000");

        assertThatThrownBy(() -> reserva.validarQueNoSacaLoCobrado(new BigDecimal("12000")))
                .isInstanceOf(LineaCobradaException.class)
                .hasMessage("La cancha tiene $15.000 cobrados: anulá o devolvé ese cobro antes de bajarle el precio.")
                .satisfies(e -> assertThat(((LineaCobradaException) e).getDatos()).containsEntry("lineaId", null));
    }
}
