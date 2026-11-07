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
}
