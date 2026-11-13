package com.LosCiruelos.padel_club_api.club.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.MANIANA;
import static com.LosCiruelos.padel_club_api.TestData.reserva;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.mapper.ProductoMapper;
import com.LosCiruelos.padel_club_api.club.dto.request.ProductoRequest;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.club.exception.StockInsuficienteException;
import com.LosCiruelos.padel_club_api.club.repository.ProductoRepository;

import jakarta.persistence.EntityManager;

class ProductoServiceTest {

    private ProductoRepository productoRepository;
    private ProductoService servicio;

    private Producto pelotas;
    private Producto paletas;

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        servicio = new ProductoService(productoRepository, new ProductoMapper(), mock(EntityManager.class));

        pelotas = Producto.builder().id(1L).codigo(Producto.CODIGO_PELOTAS).nombre("Pelotas")
                .tipo(TipoProducto.VENTA).precio(new BigDecimal("1500")).stock(10).build();
        paletas = Producto.builder().id(2L).codigo(Producto.CODIGO_PALETAS).nombre("Paletas")
                .tipo(TipoProducto.ALQUILER).precio(new BigDecimal("2000")).stock(4).build();

        when(productoRepository.findAllById(anyCollection())).thenAnswer(inv -> {
            var ids = inv.<java.util.Collection<Long>>getArgument(0);
            return List.of(pelotas, paletas).stream().filter(p -> ids.contains(p.getId())).toList();
        });
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Catalogo {

        private ProductoRequest pedido(String nombre, TipoProducto tipo) {
            ProductoRequest req = new ProductoRequest();
            req.setNombre(nombre);
            req.setTipo(tipo);
            req.setPrecio(new BigDecimal("800"));
            req.setStock(20);
            return req;
        }

        @Test
        void crearUnProducto() {
            when(productoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var r = servicio.crear(pedido("  Agua  ", TipoProducto.VENTA));

            assertThat(r.getNombre()).isEqualTo("Agua");
            assertThat(r.getActivo()).isTrue();
            assertThat(r.getCodigo()).isNull();
            // por defecto no se ofrece al reservar (lo agrega el personal) y controla stock
            assertThat(r.getOfrecerAlReservar()).isFalse();
            assertThat(r.getControlaStock()).isTrue();
        }

        @Test
        void elAdminEligeSiSeOfreceAlReservarYTodosControlanStock() {
            when(productoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            var pedido = pedido("Agua", TipoProducto.VENTA);
            pedido.setOfrecerAlReservar(true);

            var r = servicio.crear(pedido);

            assertThat(r.getOfrecerAlReservar()).isTrue();
            assertThat(r.getControlaStock()).isTrue();

            pedido.setControlaStock(false);
            assertThatThrownBy(() -> servicio.crear(pedido))
                    .isInstanceOf(ReservaException.class)
                    .hasMessage("Todos los productos controlan stock: controlaStock no puede ser false.");
            assertThatThrownBy(() -> servicio.actualizar(1L, pedido))
                    .isInstanceOf(ReservaException.class);
        }

        @Test
        void noSeRepitenNombres() {
            when(productoRepository.existsByNombreIgnoreCase("Agua")).thenReturn(true);
            assertThatThrownBy(() -> servicio.crear(pedido("Agua", TipoProducto.VENTA)))
                    .isInstanceOf(EstadoInvalidoException.class);
        }

        @Test
        void actualizarPrecioYStockPeroNoElTipo() {
            when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pelotas));
            when(productoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            var r = servicio.actualizar(1L, pedido("Pelotas", TipoProducto.VENTA));
            assertThat(r.getPrecio()).isEqualByComparingTo("800");
            assertThat(r.getStock()).isEqualTo(20);
            assertThat(r.getCodigo()).isEqualTo(Producto.CODIGO_PELOTAS);

            assertThatThrownBy(() -> servicio.actualizar(1L, pedido("Pelotas", TipoProducto.ALQUILER)))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("tipo");
        }

        @Test
        void ajustarStockNoPuedeDejarloNegativo() {
            when(productoRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(pelotas));
            when(productoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertThat(servicio.ajustarStock(1L, 12).getStock()).isEqualTo(22);
            assertThat(servicio.ajustarStock(1L, -2).getStock()).isEqualTo(20);
            assertThatThrownBy(() -> servicio.ajustarStock(1L, -21)).isInstanceOf(StockInsuficienteException.class);
        }

        @Test
        void desactivarYListar() {
            when(productoRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(paletas));
            when(productoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(productoRepository.findAllByOrderByNombreAsc()).thenReturn(List.of(paletas, pelotas));
            when(productoRepository.findAllByActivoTrueAndOfrecerAlReservarTrueOrderByNombreAsc()).thenReturn(List.of(pelotas));

            assertThat(servicio.desactivar(2L).getActivo()).isFalse();
            assertThat(servicio.listarTodos()).hasSize(2);
            assertThat(servicio.listarParaReservar()).hasSize(1);
            // se reactiva con su stock; nunca se borra
            assertThat(servicio.reactivar(2L).getActivo()).isTrue();
            verify(productoRepository, never()).delete(any());
            verify(productoRepository, never()).deleteById(any());
        }
    }

    @Nested
    class VentaSuelta {

        private Producto agua() {
            return Producto.builder().id(5L).nombre("Agua").tipo(TipoProducto.VENTA).precio(new BigDecimal("1500"))
                    .stock(8).build();
        }

        @Test
        void descuentaElStockDeTodos() {
            Producto agua = agua();
            java.util.Map<Producto, Integer> cantidades = new java.util.LinkedHashMap<>();
            cantidades.put(pelotas, 3);
            cantidades.put(agua, 5);

            servicio.descontarVentaSuelta(cantidades);

            assertThat(pelotas.getStock()).isEqualTo(7);
            assertThat(agua.getStock()).isEqualTo(3);
        }

        @Test
        void unProductoEscritoAManoNaceConElStockDeLoQueSeVende() {
            when(productoRepository.findFirstByNombreIgnoreCase("Barrita")).thenReturn(java.util.Optional.empty());
            when(productoRepository.save(any())).thenAnswer(inv -> {
                Producto p = inv.getArgument(0);
                p.setId(40L);
                return p;
            });

            Producto nuevo = servicio.productoManual(" Barrita ", new BigDecimal("1200"), 3);

            assertThat(nuevo.getControlaStock()).isTrue();
            assertThat(nuevo.getStock()).isEqualTo(3);
            // al venderlo queda en 0
            servicio.descontarVentaSuelta(java.util.Map.of(nuevo, 3));
            assertThat(nuevo.getStock()).isZero();
        }

        @Test
        void sinStockSuficienteNoSeVende() {
            assertThatThrownBy(() -> servicio.descontarVentaSuelta(java.util.Map.of(pelotas, 11)))
                    .isInstanceOf(StockInsuficienteException.class)
                    .hasMessage("Pelotas: hay 10 unidades en stock.");
            assertThat(pelotas.getStock()).isEqualTo(10);
        }

        @Test
        void unProductoDadoDeBajaNoSeVende() {
            pelotas.setActivo(false);
            assertThatThrownBy(() -> servicio.descontarVentaSuelta(java.util.Map.of(pelotas, 1)))
                    .isInstanceOf(ReservaException.class)
                    .hasMessage("Pelotas no está disponible por el momento.");
        }
    }

    // ── Eliminar ─────────────────────────────────────────────────────────

    @org.junit.jupiter.api.Test
    void unProductoQueNuncaSeUsoSeElimina() {
        Producto agua = Producto.builder().id(7L).nombre("Agua").tipo(com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto.VENTA)
                .precio(new java.math.BigDecimal("1500")).stock(0).activo(true).build();
        when(productoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(agua));
        when(productoRepository.seUso(7L)).thenReturn(false);

        servicio.eliminar(7L);

        verify(productoRepository).delete(agua);
    }

    @org.junit.jupiter.api.Test
    void unProductoUsadoNoSeEliminaSeDaDeBaja() {
        Producto agua = Producto.builder().id(7L).nombre("Agua").tipo(com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto.VENTA)
                .precio(new java.math.BigDecimal("1500")).stock(0).activo(true).build();
        when(productoRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(agua));
        when(productoRepository.seUso(7L)).thenReturn(true);

        assertThatThrownBy(() -> servicio.eliminar(7L))
                .isInstanceOf(com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException.class)
                .hasMessage("El producto ya se usó en reservas o ventas: desactivalo en lugar de eliminarlo.");
        verify(productoRepository, never()).delete(any());
    }
}
