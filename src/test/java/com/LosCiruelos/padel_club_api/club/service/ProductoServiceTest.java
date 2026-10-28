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
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Exceptions.StockInsuficienteException;
import com.LosCiruelos.padel_club_api.club.repository.ProductoRepository;

class ProductoServiceTest {

    private ProductoRepository productoRepository;
    private ProductoService servicio;

    private Producto pelotas;
    private Producto paletas;

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        servicio = new ProductoService(productoRepository, new ProductoMapper());

        pelotas = Producto.builder().id(1L).codigo(Producto.CODIGO_PELOTAS).nombre("Pelotas")
                .tipo(TipoProducto.VENTA).precio(new BigDecimal("1500")).stock(10).build();
        paletas = Producto.builder().id(2L).codigo(Producto.CODIGO_PALETAS).nombre("Paletas")
                .tipo(TipoProducto.ALQUILER).precio(new BigDecimal("2000")).stock(4).build();

        when(productoRepository.findByCodigoIn(anyCollection())).thenAnswer(inv -> {
            var codigos = inv.<java.util.Collection<String>>getArgument(0);
            return List.of(pelotas, paletas).stream().filter(p -> codigos.contains(p.getCodigo())).toList();
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
            when(productoRepository.findAllByActivoTrueOrderByNombreAsc()).thenReturn(List.of(pelotas));

            assertThat(servicio.desactivar(2L).getActivo()).isFalse();
            assertThat(servicio.listarTodos()).hasSize(2);
            assertThat(servicio.listarActivos()).hasSize(1);
        }
    }
}
