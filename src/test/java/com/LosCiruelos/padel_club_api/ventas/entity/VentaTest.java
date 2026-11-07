package com.LosCiruelos.padel_club_api.ventas.entity;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.HOY;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

class VentaTest {

    private final Producto agua = Producto.builder().id(5L).nombre("Agua").tipo(TipoProducto.VENTA)
            .precio(new BigDecimal("1500")).build();
    private final Producto pelotas = Producto.builder().id(1L).nombre("Pelotas").tipo(TipoProducto.VENTA)
            .precio(new BigDecimal("8000")).build();

    @Test
    void elTotalEsLaSumaDeLosItemsAlPrecioDeHoy() {
        Venta venta = new Venta();
        venta.agregar(agua, 2);
        venta.agregar(pelotas, 1);

        assertThat(venta.getTotal()).isEqualByComparingTo("11000");
        assertThat(venta.getItems()).allSatisfy(i -> assertThat(i.getVenta()).isSameAs(venta));
    }

    @Test
    void unProductoRepetidoSumaUnidadesAlMismoItem() {
        Venta venta = new Venta();
        venta.agregar(agua, 2);
        agua.setPrecio(new BigDecimal("2000"));
        venta.agregar(agua, 1);

        assertThat(venta.getItems()).singleElement().satisfies(i -> {
            assertThat(i.getCantidad()).isEqualTo(3);
            assertThat(i.getPrecioUnitario()).isEqualByComparingTo("1500"); // el del primer ítem
        });
        assertThat(venta.getTotal()).isEqualByComparingTo("4500");
    }

    @Nested
    class Anular {

        private final Usuario admin = usuario(1L, Role.ADMIN);
        private final Usuario empleado = usuario(2L, Role.EMPLEADO);

        private Venta venta(java.time.LocalDateTime cuando, MedioDePago medio) {
            Venta venta = Venta.builder().id(7L).createdAt(cuando).medio(medio).registradoPor(empleado).build();
            venta.agregar(agua, 2);
            venta.agregar(pelotas, 1);
            return venta;
        }

        @Test
        void unaVentaNuevaEstaCobrada() {
            assertThat(new Venta().getEstado()).isEqualTo(EstadoVenta.COBRADA);
        }

        @Test
        void quedaAnuladaConQuienCuandoYPorQue() {
            Venta venta = venta(AHORA, MedioDePago.EFECTIVO);

            venta.anular(empleado, "Error de carga", AHORA.plusMinutes(5));

            assertThat(venta.estaAnulada()).isTrue();
            assertThat(venta.getAnuladaPor()).isSameAs(empleado);
            assertThat(venta.getAnuladaAt()).isEqualTo(AHORA.plusMinutes(5));
            assertThat(venta.getMotivoAnulacion()).isEqualTo("Error de carga");
            assertThat(venta.cantidades()).containsExactly(java.util.Map.entry(agua, 2), java.util.Map.entry(pelotas, 1));
        }

        @Test
        void noSeAnulaDosVeces() {
            Venta venta = venta(AHORA, MedioDePago.EFECTIVO);
            venta.anular(empleado, "Error de carga", AHORA);

            assertThatThrownBy(() -> venta.anular(admin, "Otra vez", AHORA))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("La venta ya está anulada.");
        }

        @Test
        void elEmpleadoSoloAnulaLasDeHoy() {
            venta(AHORA.minusHours(9), MedioDePago.EFECTIVO).validarQuePuedeAnularla(empleado, HOY);

            assertThatThrownBy(() -> venta(AHORA.minusDays(1), MedioDePago.EFECTIVO)
                    .validarQuePuedeAnularla(empleado, HOY))
                    .isInstanceOf(PermisoDenegadoException.class)
                    .hasMessage("Solo podés anular ventas de hoy.");
        }

        @Test
        void elAdminAnulaCualquierDia() {
            venta(AHORA.minusDays(30), MedioDePago.EFECTIVO).validarQuePuedeAnularla(admin, HOY);
        }

        @Test
        void unaVentaVerificadaSoloLaAnulaElAdmin() {
            Venta dni = venta(AHORA, MedioDePago.CUENTA_DNI);
            dni.verificar(true, 1L, AHORA);

            assertThatThrownBy(() -> dni.validarQuePuedeAnularla(empleado, HOY))
                    .isInstanceOf(PermisoDenegadoException.class)
                    .hasMessage("La venta ya está verificada: solo el ADMIN la puede anular.");
            dni.validarQuePuedeAnularla(admin, HOY);
        }

        @Test
        void soloSeCambiaElClienteDeUnaVentaVigente() {
            Venta venta = venta(AHORA, MedioDePago.EFECTIVO);
            Usuario cliente = usuario(3L, Role.CLIENTE);

            venta.cambiarCliente(cliente);
            assertThat(venta.getCliente()).isSameAs(cliente);
            venta.cambiarCliente(null);
            assertThat(venta.getCliente()).isNull();

            venta.anular(admin, "Error de carga", AHORA);
            assertThatThrownBy(() -> venta.cambiarCliente(cliente))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("La venta está anulada: no se puede modificar.");
        }
    }

    @Nested
    class CobroConQr {

        private final Usuario admin = usuario(1L, Role.ADMIN);

        private Venta esperando() {
            Venta venta = Venta.builder().id(7L).createdAt(AHORA).medio(MedioDePago.MERCADO_PAGO).registradoPor(admin)
                    .orderId("ORD1").build();
            venta.agregar(agua, 1);
            venta.esperarPagoConQr(AHORA.plusMinutes(10));
            return venta;
        }

        @Test
        void esperaElPagoHastaQueVence() {
            Venta venta = esperando();

            assertThat(venta.getEstado()).isEqualTo(EstadoVenta.ESPERANDO_PAGO);
            assertThat(venta.esQrActivo(AHORA.plusMinutes(9))).isTrue();
            assertThat(venta.esQrActivo(AHORA.plusMinutes(10))).isFalse();
            assertThat(venta.seCobroConQr()).isTrue();
            assertThat(venta.getClaveIdempotencia()).isNotNull();
        }

        @Test
        void seCobraOQuedaSinCobrarUnaSolaVez() {
            Venta cobrada = esperando();
            cobrada.confirmarCobro();
            assertThat(cobrada.getEstado()).isEqualTo(EstadoVenta.COBRADA);
            assertThatThrownBy(cobrada::marcarNoCobrada).isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("La venta no está esperando el pago con QR.");

            Venta sinCobrar = esperando();
            sinCobrar.marcarNoCobrada();
            assertThat(sinCobrar.getEstado()).isEqualTo(EstadoVenta.NO_COBRADA);
            assertThatThrownBy(sinCobrar::confirmarCobro).isInstanceOf(EstadoInvalidoException.class);
        }

        @Test
        void mientrasEsperaNoSeAnulaNiSeDevuelve() {
            Venta venta = esperando();

            assertThatThrownBy(() -> venta.anular(admin, "Error", AHORA))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("La venta está esperando el pago con QR: cancelá el QR antes.");
            assertThatThrownBy(() -> venta.devolver(java.util.List.of(new VentaDevolucion.Pedido(5L, 1, true)),
                    "x", admin, AHORA))
                    .hasMessage("La venta está esperando el pago con QR: cancelá el QR antes.");
            venta.cambiarCliente(usuario(3L, Role.CLIENTE)); // el cliente sí
        }

        @Test
        void unaNoCobradaNoSeAnulaNiSeDevuelveNiSeModifica() {
            Venta venta = esperando();
            venta.marcarNoCobrada();

            assertThatThrownBy(() -> venta.anular(admin, "Error", AHORA))
                    .hasMessage("La venta no se cobró: no hay nada que anular.");
            assertThatThrownBy(() -> venta.devolver(java.util.List.of(new VentaDevolucion.Pedido(5L, 1, true)),
                    "x", admin, AHORA))
                    .hasMessage("La venta no se cobró: no hay nada que devolver.");
            assertThatThrownBy(() -> venta.cambiarCliente(null))
                    .hasMessage("La venta no se cobró: no se puede modificar.");
        }
    }

    @Nested
    class Devolver {

        private final Usuario admin = usuario(1L, Role.ADMIN);
        private Venta venta;

        @org.junit.jupiter.api.BeforeEach
        void unaVenta() {
            venta = Venta.builder().id(7L).createdAt(AHORA.minusDays(2)).medio(MedioDePago.EFECTIVO)
                    .registradoPor(admin).build();
            venta.agregar(agua, 3);
            venta.agregar(pelotas, 1);
        }

        private VentaDevolucion devolver(Producto producto, int cantidad, boolean vuelve) {
            return venta.devolver(java.util.List.of(new VentaDevolucion.Pedido(producto.getId(), cantidad, vuelve)),
                    "Vencido", admin, AHORA);
        }

        @Test
        void unaDevolucionParcialAlPrecioDeLaVenta() {
            agua.setPrecio(new BigDecimal("9999")); // el precio de hoy no importa

            VentaDevolucion d = venta.devolver(java.util.List.of(new VentaDevolucion.Pedido(5L, 2, true),
                    new VentaDevolucion.Pedido(1L, 1, false)), "Vencido", admin, AHORA);

            assertThat(d.getMonto()).isEqualByComparingTo("11000"); // 2 × 1.500 + 8.000
            assertThat(d.getCreatedAt()).isEqualTo(AHORA);
            assertThat(d.getRegistradaPor()).isSameAs(admin);
            assertThat(d.aReponer()).containsExactly(java.util.Map.entry(agua, 2));
            assertThat(venta.getItems()).extracting(VentaItem::getDevueltas).containsExactly(2, 1);
            assertThat(venta.montoDevuelto()).isEqualByComparingTo("11000");
            assertThat(venta.getTotal()).isEqualByComparingTo("12500"); // el total de la venta no cambia
        }

        @Test
        void noSeDevuelveMasDeLoQueQueda() {
            devolver(agua, 2, true);

            assertThatThrownBy(() -> devolver(agua, 2, true))
                    .isInstanceOf(ReservaException.class).hasMessage("De Agua queda 1 unidad por devolver.");
            devolver(agua, 1, true);
            assertThatThrownBy(() -> devolver(agua, 1, true))
                    .isInstanceOf(ReservaException.class).hasMessage("Agua ya se devolvió entero.");
            assertThat(venta.getDevoluciones()).hasSize(2);
        }

        @Test
        void siUnItemNoSePuedeNoSeDevuelveNada() {
            assertThatThrownBy(() -> venta.devolver(java.util.List.of(new VentaDevolucion.Pedido(5L, 1, true),
                    new VentaDevolucion.Pedido(1L, 2, true)), "Vencido", admin, AHORA))
                    .isInstanceOf(ReservaException.class)
                    .hasMessage("De Pelotas queda 1 unidad por devolver.");
            assertThat(venta.getItems()).extracting(VentaItem::getDevueltas).containsExactly(0, 0);
            assertThat(venta.getDevoluciones()).isEmpty();
        }

        @Test
        void soloProductosDeLaVentaYSinRepetir() {
            assertThatThrownBy(() -> venta.devolver(java.util.List.of(new VentaDevolucion.Pedido(9L, 1, true)),
                    "Vencido", admin, AHORA))
                    .isInstanceOf(ReservaException.class).hasMessage("El producto #9 no está en la venta.");
            assertThatThrownBy(() -> venta.devolver(java.util.List.of(new VentaDevolucion.Pedido(5L, 1, true),
                    new VentaDevolucion.Pedido(5L, 1, false)), "Vencido", admin, AHORA))
                    .isInstanceOf(ReservaException.class).hasMessage("Agua está repetido en la devolución.");
        }

        @Test
        void unaVentaAnuladaNoSeDevuelveYUnaConDevolucionesNoSeAnula() {
            devolver(agua, 1, true);
            assertThatThrownBy(() -> venta.anular(admin, "Error", AHORA))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("La venta tiene devoluciones: no se puede anular. Registrá una devolución por lo que queda.");

            Venta otra = Venta.builder().createdAt(AHORA).medio(MedioDePago.EFECTIVO).registradoPor(admin).build();
            otra.agregar(agua, 1);
            otra.anular(admin, "Error", AHORA);
            assertThatThrownBy(() -> otra.devolver(java.util.List.of(new VentaDevolucion.Pedido(5L, 1, true)),
                    "Vencido", admin, AHORA))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("La venta está anulada: no se puede devolver.");
        }
    }
}
