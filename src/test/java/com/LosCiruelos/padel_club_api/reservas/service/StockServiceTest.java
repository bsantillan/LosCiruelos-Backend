package com.LosCiruelos.padel_club_api.reservas.service;

import com.LosCiruelos.padel_club_api.reservas.dto.request.ProductoPedidoRequest;
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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.mapper.ProductoMapper;
import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ProductoDisponibleResponse;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.club.exception.StockInsuficienteException;
import com.LosCiruelos.padel_club_api.club.repository.ProductoRepository;
import com.LosCiruelos.padel_club_api.club.service.ProductoService;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaProducto;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.UsoAlquiler;

import jakarta.persistence.EntityManager;

class StockServiceTest {

    private ProductoRepository productoRepository;
    private ReservaRepository reservaRepository;
    private StockService servicio;

    private Producto pelotas;
    private Producto paletas;

    @BeforeEach
    void setUp() {
        productoRepository = mock(ProductoRepository.class);
        reservaRepository = mock(ReservaRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        servicio = new StockService(new ProductoService(productoRepository, new ProductoMapper()), reservaRepository,
                entityManager, TestData.reloj(AHORA));

        pelotas = Producto.builder().id(1L).codigo(Producto.CODIGO_PELOTAS).nombre("Pelotas")
                .tipo(TipoProducto.VENTA).precio(new BigDecimal("1500")).stock(10).build();
        paletas = Producto.builder().id(2L).codigo(Producto.CODIGO_PALETAS).nombre("Paletas")
                .tipo(TipoProducto.ALQUILER).precio(new BigDecimal("2000")).stock(4).build();

        when(entityManager.find(Producto.class, 1L)).thenReturn(pelotas);
        when(entityManager.find(Producto.class, 2L)).thenReturn(paletas);
        when(productoRepository.findAllById(anyCollection())).thenAnswer(inv -> {
            var ids = inv.<java.util.Collection<Long>>getArgument(0);
            return List.of(pelotas, paletas).stream().filter(p -> ids.contains(p.getId())).toList();
        });
        alquileres(List.of());
    }

    private void alquileres(List<UsoAlquiler> usos) {
        when(reservaRepository.findAlquileresQueSeSuperponen(eq(2L), anyLong(), any(), any(), any(), any(),
                anyCollection(), any(), anyCollection())).thenReturn(usos);
    }

    private static UsoAlquiler uso(String inicio, String fin, int cantidad) {
        return new UsoAlquiler(LocalTime.parse(inicio), LocalTime.parse(fin), cantidad);
    }

    private Reserva nuevaReserva() {
        Reserva r = reserva(null, usuario(1L, Role.CLIENTE), EstadoReserva.PENDIENTE, "15000"); // 18:00-19:30
        r.setId(null);
        return r;
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class UsoMaximo {

        @Test
        void turnosConsecutivosNoSeSuman() {
            // 2 paletas 18:00-19:30 y 2 de 19:30-21:00: nunca están en uso al mismo tiempo
            assertThat(StockService.usoMaximo(List.of(uso("18:00", "19:30", 2), uso("19:30", "21:00", 2)),
                    LocalTime.of(18, 30), LocalTime.of(20, 0))).isEqualTo(2);
        }

        @Test
        void turnosQueSePisanSeSuman() {
            assertThat(StockService.usoMaximo(List.of(uso("18:00", "19:30", 2), uso("19:00", "20:30", 1)),
                    LocalTime.of(18, 0), LocalTime.of(21, 0))).isEqualTo(3);
        }

        @Test
        void sinAlquileresEsCero() {
            assertThat(StockService.usoMaximo(List.of(), LocalTime.of(18, 0), LocalTime.of(19, 30))).isZero();
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class StockDeReservas {

        @Test
        void ventaDescuentaStockYCongelaElPrecio() {
            Reserva reserva = nuevaReserva();

            servicio.moverStock(List.of(), reserva, Map.of(1L, 3), AHORA);

            assertThat(pelotas.getStock()).isEqualTo(7);
            assertThat(reserva.cantidadDe(Producto.CODIGO_PELOTAS)).isEqualTo(3);
            assertThat(reserva.getMontoProductos()).isEqualByComparingTo("4500");

            pelotas.setPrecio(new BigDecimal("9999"));
            assertThat(reserva.getMontoProductos()).isEqualByComparingTo("4500");
        }

        @Test
        void unProductoQueNoExisteEs400YElClienteSoloPideLoQueSeOfrece() {
            assertThatThrownBy(() -> servicio.moverStock(List.of(), nuevaReserva(), Map.of(99L, 1), AHORA))
                    .isInstanceOf(ReservaException.class)
                    .hasMessage("El producto 99 no existe.");

            pelotas.setOfrecerAlReservar(true);
            paletas.setOfrecerAlReservar(false);
            servicio.exigirQueSeOfrezcan(Map.of(1L, 2));
            assertThatThrownBy(() -> servicio.exigirQueSeOfrezcan(Map.of(1L, 2, 2L, 1)))
                    .isInstanceOf(ReservaException.class)
                    .hasMessage("Paletas no se puede pedir al reservar: pedíselo al club.");
        }

        @Test
        void losProductosRepetidosEnElPedidoSeSuman() {
            assertThat(StockService.pedido(List.of(new ProductoPedidoRequest(1L, 2), new ProductoPedidoRequest(1L, 3),
                    new ProductoPedidoRequest(2L, 1)))).isEqualTo(Map.of(1L, 5, 2L, 1));
            assertThat(StockService.pedido(null)).isEmpty();
        }

        @Test
        void unProductoSinControlDeStockSePideSinLimiteYNoMueveElStock() {
            pelotas.setControlaStock(false);
            Reserva reserva = nuevaReserva();

            servicio.moverStock(List.of(), reserva, Map.of(1L, 50), AHORA);

            assertThat(reserva.cantidadDe(Producto.CODIGO_PELOTAS)).isEqualTo(50);
            assertThat(pelotas.getStock()).isEqualTo(10);
            servicio.liberar(reserva);
            assertThat(pelotas.getStock()).isEqualTo(10);
        }

        @Test
        void ventaSinStockSuficiente() {
            assertThatThrownBy(() -> servicio.moverStock(List.of(), nuevaReserva(),
                    Map.of(1L, 11), AHORA))
                    .isInstanceOf(StockInsuficienteException.class)
                    .hasMessageContaining("Pelotas: hay 10 unidades en stock");
            assertThat(pelotas.getStock()).isEqualTo(10);
        }

        @Test
        void alquilerControlaLasUnidadesEnUsoEnEseHorarioSinTocarElStock() {
            alquileres(List.of(uso("17:00", "18:30", 2), uso("19:00", "20:00", 1)));

            // de 18:00 a 19:30 hay como máximo 2 en uso: quedan 2 de 4
            assertThatThrownBy(() -> servicio.moverStock(List.of(), nuevaReserva(),
                    Map.of(2L, 3), AHORA))
                    .isInstanceOf(StockInsuficienteException.class)
                    .hasMessageContaining("Paletas: hay 2 unidades disponibles para ese horario");

            Reserva reserva = nuevaReserva();
            servicio.moverStock(List.of(), reserva, Map.of(2L, 2), AHORA);
            assertThat(reserva.cantidadDe(Producto.CODIGO_PALETAS)).isEqualTo(2);
            assertThat(reserva.getMontoProductos()).isEqualByComparingTo("4000");
            assertThat(paletas.getStock()).isEqualTo(4);
        }

        @Test
        void lasPaletasDeTurnosTerminadosQueNoVolvieronNoSePuedenAlquilar() {
            // 4 paletas: 3 de un turno de esta mañana todavía no se devolvieron
            when(reservaRepository.sumarSinDevolver(eq(2L), anyCollection(), any(), any())).thenReturn(3L);

            assertThatThrownBy(() -> servicio.moverStock(List.of(), nuevaReserva(),
                    Map.of(2L, 2), AHORA))
                    .isInstanceOf(StockInsuficienteException.class)
                    .hasMessageContaining("1 unidad");
        }

        @Test
        void devolucionCompletaNoTocaElStockYConFaltantesLoBaja() {
            Reserva reserva = nuevaReserva();
            servicio.moverStock(List.of(), reserva, Map.of(2L, 3), AHORA);
            ReservaProducto item = reserva.getProductos().getFirst();

            servicio.registrarDevolucion(item, 3, AHORA);
            assertThat(paletas.getStock()).isEqualTo(4);
            assertThat(item.getCantidadDevuelta()).isEqualTo(3);
            assertThat(item.getDevueltaAt()).isEqualTo(AHORA);

            item.setCantidadDevuelta(null);
            servicio.registrarDevolucion(item, 1, AHORA); // se rompieron 2
            assertThat(paletas.getStock()).isEqualTo(2);
        }

        @Test
        void productoInactivoNoSePuedePedir() {
            paletas.setActivo(false);

            assertThatThrownBy(() -> servicio.moverStock(List.of(), nuevaReserva(),
                    Map.of(2L, 1), AHORA))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("no está disponible");
        }

        @Test
        void sinProductosPedidosNoHaceNada() {
            Reserva reserva = nuevaReserva();
            servicio.moverStock(List.of(), reserva, Map.of(1L, 0), AHORA);
            assertThat(reserva.getProductos()).isEmpty();
        }

        @Test
        void alCancelarOExpirarSeDevuelveSoloLoVendido() {
            Reserva reserva = nuevaReserva();
            servicio.moverStock(List.of(), reserva,
                    Map.of(1L, 3, 2L, 2), AHORA);
            assertThat(pelotas.getStock()).isEqualTo(7);

            servicio.liberar(reserva);

            assertThat(pelotas.getStock()).isEqualTo(10);
            assertThat(paletas.getStock()).isEqualTo(4);
        }

        @Test
        void modificarActualizaLosItemsEnElLugarYAjustaElStock() {
            Reserva reserva = nuevaReserva();
            servicio.moverStock(List.of(), reserva, Map.of(1L, 2), AHORA);
            ReservaProducto item = reserva.getProductos().getFirst();
            assertThat(pelotas.getStock()).isEqualTo(8);

            servicio.moverStock(List.of(), reserva, Map.of(1L, 5), AHORA);

            assertThat(reserva.getProductos()).containsExactly(item); // mismo ítem, no se recrea
            assertThat(item.getCantidad()).isEqualTo(5);
            assertThat(pelotas.getStock()).isEqualTo(5);

            servicio.moverStock(List.of(), reserva, Map.of(1L, 0), AHORA);
            assertThat(reserva.getProductos()).isEmpty();
            assertThat(pelotas.getStock()).isEqualTo(10);
        }

        @Test
        void liberarOtrasReservasYTomarParaLaNuevaEnUnSoloPaso() {
            Reserva vencida = nuevaReserva();
            servicio.moverStock(List.of(), vencida, Map.of(1L, 10), AHORA);
            assertThat(pelotas.getStock()).isZero();

            // las pelotas de la reserva vencida vuelven y alcanzan para la nueva
            Reserva nueva = nuevaReserva();
            servicio.moverStock(List.of(vencida), nueva, Map.of(1L, 4), AHORA);

            assertThat(pelotas.getStock()).isEqualTo(6);
            assertThat(nueva.cantidadDe(Producto.CODIGO_PELOTAS)).isEqualTo(4);
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Disponibilidad {

        @Test
        void disponibilidadPorHorario() {
            when(productoRepository.findAllByActivoTrueAndOfrecerAlReservarTrueOrderByNombreAsc()).thenReturn(List.of(paletas, pelotas));
            alquileres(List.of(uso("18:00", "19:30", 3)));

            List<ProductoDisponibleResponse> d = servicio.disponibilidad(MANIANA, LocalTime.of(18, 0),
                    LocalTime.of(19, 30));

            assertThat(d).extracting(ProductoDisponibleResponse::getDisponibles).containsExactly(1, 10);

            // sin control de stock: sin límite
            pelotas.setControlaStock(false);
            assertThat(servicio.disponibilidad(MANIANA, LocalTime.of(18, 0), LocalTime.of(19, 30)))
                    .extracting(ProductoDisponibleResponse::getDisponibles).containsExactly(1, null);
            assertThatThrownBy(() -> servicio.disponibilidad(MANIANA, LocalTime.of(19, 0), LocalTime.of(18, 0)))
                    .isInstanceOf(ReservaException.class);
        }
    }
}
