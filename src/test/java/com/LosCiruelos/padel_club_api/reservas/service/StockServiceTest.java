package com.LosCiruelos.padel_club_api.reservas.service;

import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import java.util.Optional;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        servicio = new StockService(new ProductoService(productoRepository, new ProductoMapper(), entityManager), reservaRepository,
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
        void todosLosProductosLimitanLasUnidades() {
            Reserva reserva = nuevaReserva();

            assertThatThrownBy(() -> servicio.moverStock(List.of(), reserva, Map.of(1L, 50), AHORA))
                    .isInstanceOf(com.LosCiruelos.padel_club_api.club.exception.StockInsuficienteException.class);
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

            // todos controlan stock: disponibles nunca es null
            pelotas.setStock(0);
            assertThat(servicio.disponibilidad(MANIANA, LocalTime.of(18, 0), LocalTime.of(19, 30)))
                    .extracting(ProductoDisponibleResponse::getDisponibles).containsExactly(1, 0);
            assertThatThrownBy(() -> servicio.disponibilidad(MANIANA, LocalTime.of(19, 0), LocalTime.of(18, 0)))
                    .isInstanceOf(ReservaException.class);
        }

        @Test
        void elClienteVeQueSeOfrecenAlReservar() {
            pelotas.setOfrecerAlReservar(true);
            when(productoRepository.findAllByActivoTrueAndOfrecerAlReservarTrueOrderByNombreAsc()).thenReturn(List.of(pelotas));

            assertThat(servicio.disponibilidad(MANIANA, LocalTime.of(18, 0), LocalTime.of(19, 30)))
                    .extracting(ProductoDisponibleResponse::getOfrecerAlReservar).containsExactly(true);
        }
    }

    @Nested
    class DisponibilidadParaElPersonal {

        private Producto agua;
        private Producto raquetero;

        @BeforeEach
        void todosLosActivos() {
            pelotas.setOfrecerAlReservar(true);
            paletas.setOfrecerAlReservar(true);
            // una venta sin stock y un alquiler que no se ofrece al reservar
            agua = Producto.builder().id(5L).nombre("Agua").tipo(TipoProducto.VENTA).precio(new BigDecimal("1500"))
                    .stock(0).ofrecerAlReservar(false).build();
            raquetero = Producto.builder().id(6L).nombre("Paleta premium").tipo(TipoProducto.ALQUILER)
                    .precio(new BigDecimal("4000")).stock(3).ofrecerAlReservar(false).build();
            when(productoRepository.findAllByActivoTrueOrderByIdAsc()).thenReturn(List.of(pelotas, paletas, agua, raquetero));
            when(reservaRepository.findAlquileresQueSeSuperponen(eq(6L), anyLong(), any(), any(), any(), any(),
                    anyCollection(), any(), anyCollection())).thenReturn(List.of(uso("17:00", "18:30", 2)));
        }

        private List<ProductoDisponibleResponse> disponibilidad(Long excluir) {
            return servicio.disponibilidadParaPersonal(MANIANA, LocalTime.of(18, 0), LocalTime.of(19, 30), excluir);
        }

        @Test
        void traeTodosLosActivosEnOrdenDeIdConSuDisponibilidad() {
            alquileres(List.of(uso("18:00", "19:30", 3), uso("19:00", "20:00", 0)));

            List<ProductoDisponibleResponse> d = disponibilidad(null);

            assertThat(d).extracting(ProductoDisponibleResponse::getId).containsExactly(1L, 2L, 5L, 6L);
            // VENTA: el stock; ALQUILER: total menos lo que se superpone (en cualquier cancha); sin stock: 0
            assertThat(d).extracting(ProductoDisponibleResponse::getDisponibles).containsExactly(10, 1, 0, 1);
            assertThat(d).extracting(ProductoDisponibleResponse::getOfrecerAlReservar)
                    .containsExactly(true, true, false, false);
        }

        @Test
        void loQueYaTieneLaReservaQueSeEditaCuentaComoLibre() {
            Reserva editada = reserva(50L, usuario(1L, Role.CLIENTE), EstadoReserva.SEÑADA, "15000");
            editada.getProductos().add(ReservaProducto.builder().reserva(editada).producto(pelotas).cantidad(3)
                    .precioUnitario(new BigDecimal("1500")).build());
            when(reservaRepository.findById(50L)).thenReturn(java.util.Optional.of(editada));
            // la consulta de alquileres ya no cuenta los de la reserva 50
            when(reservaRepository.findAlquileresQueSeSuperponen(eq(2L), eq(50L), any(), any(), any(), any(),
                    anyCollection(), any(), anyCollection())).thenReturn(List.of());

            List<ProductoDisponibleResponse> d = disponibilidad(50L);

            // las 3 pelotas de la reserva vuelven al stock, como al guardar (moverStock)
            assertThat(d).extracting(ProductoDisponibleResponse::getDisponibles).containsExactly(13, 4, 0, 1);
            verify(reservaRepository).findAlquileresQueSeSuperponen(eq(6L), eq(50L), any(), any(), any(), any(),
                    anyCollection(), any(), anyCollection());
        }

        @Test
        void unaReservaCanceladaYaDevolvioSuStockDeVenta() {
            Reserva cancelada = reserva(51L, usuario(1L, Role.CLIENTE), EstadoReserva.CANCELADA, "15000");
            cancelada.getProductos().add(ReservaProducto.builder().reserva(cancelada).producto(pelotas).cantidad(3)
                    .precioUnitario(new BigDecimal("1500")).build());
            when(reservaRepository.findById(51L)).thenReturn(java.util.Optional.of(cancelada));

            assertThat(disponibilidad(51L).get(0).getDisponibles()).isEqualTo(10);
        }

        @Test
        void unaReservaQueNoExisteSeIgnora() {
            when(reservaRepository.findById(99L)).thenReturn(java.util.Optional.empty());

            assertThat(disponibilidad(99L)).extracting(ProductoDisponibleResponse::getDisponibles)
                    .containsExactly(10, 4, 0, 1);
            verify(reservaRepository, never()).findAlquileresQueSeSuperponen(anyLong(), eq(99L), any(), any(), any(),
                    any(), anyCollection(), any(), anyCollection());
        }

        @Test
        void elHorarioTieneQueSerValido() {
            assertThatThrownBy(() -> servicio.disponibilidadParaPersonal(MANIANA, LocalTime.of(19, 0),
                    LocalTime.of(19, 0), null))
                    .isInstanceOf(ReservaException.class)
                    .hasMessage("La hora de inicio debe ser anterior a la hora de fin.");
        }

        @Test
        void noTieneElTopeDeAnticipacionDelCliente() {
            assertThat(servicio.disponibilidadParaPersonal(MANIANA.plusDays(200), LocalTime.of(18, 0),
                    LocalTime.of(19, 30), null)).hasSize(4);
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class SumarYSacar {

        @Test
        void sumarMantieneElPrecioDeLoQueYaTeniaYDescuentaElStock() {
            Reserva reserva = nuevaReserva();
            servicio.moverStock(List.of(), reserva, Map.of(1L, 2), AHORA); // 2 pelotas a $1500
            pelotas.setPrecio(new BigDecimal("1800"));

            servicio.agregar(reserva, pelotas, 3, AHORA);

            assertThat(reserva.cantidadDe(Producto.CODIGO_PELOTAS)).isEqualTo(5);
            assertThat(reserva.getMontoProductos()).isEqualByComparingTo("7500"); // sigue a $1500
            assertThat(pelotas.getStock()).isEqualTo(5);
            assertThatThrownBy(() -> servicio.agregar(reserva, pelotas, 6, AHORA))
                    .isInstanceOf(StockInsuficienteException.class)
                    .hasMessage("Pelotas: hay 5 unidades en stock.");
        }

        @Test
        void unAlquilerCuentaLoQueYaTeniaLaReserva() {
            Reserva reserva = nuevaReserva();
            reserva.setId(5L);
            servicio.agregar(reserva, paletas, 3, AHORA);

            assertThatThrownBy(() -> servicio.agregar(reserva, paletas, 2, AHORA))
                    .isInstanceOf(StockInsuficienteException.class)
                    .hasMessage("Paletas: hay 4 unidades disponibles para ese horario.");
        }

        @Test
        void sacarDevuelveElStockYNoSePuedeSacarUnAlquilerYaDevuelto() {
            Reserva reserva = nuevaReserva();
            servicio.agregar(reserva, pelotas, 4, AHORA);

            servicio.quitar(reserva, 1L, 1);
            assertThat(reserva.cantidadDe(Producto.CODIGO_PELOTAS)).isEqualTo(3);
            assertThat(pelotas.getStock()).isEqualTo(7);
            servicio.quitar(reserva, 1L, null);
            assertThat(reserva.getProductos()).isEmpty();
            assertThat(pelotas.getStock()).isEqualTo(10);

            servicio.agregar(reserva, paletas, 2, AHORA);
            reserva.getProductos().getFirst().setCantidadDevuelta(2);
            assertThatThrownBy(() -> servicio.quitar(reserva, 2L, null))
                    .isInstanceOf(EstadoInvalidoException.class);
            assertThatThrownBy(() -> servicio.quitar(reserva, 99L, null))
                    .isInstanceOf(ReservaException.class)
                    .hasMessage("La reserva no tiene el producto 99.");
        }

        @Test
        void unProductoEscritoAManoSeDaDeAlta() {
            when(productoRepository.findFirstByNombreIgnoreCase("Gatorade")).thenReturn(Optional.empty());
            when(productoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Producto nuevo = servicio.productoParaAgregar(null, "  Gatorade ", new BigDecimal("3000"), 2);

            assertThat(nuevo.getNombre()).isEqualTo("Gatorade");
            assertThat(nuevo.getTipo()).isEqualTo(TipoProducto.VENTA);
            assertThat(nuevo.getControlaStock()).isTrue();
            assertThat(nuevo.getStock()).isEqualTo(2); // lo que se agrega ahora: queda en 0
            assertThat(nuevo.getOfrecerAlReservar()).isFalse();
            assertThatThrownBy(() -> servicio.productoParaAgregar(null, "Gatorade", null, 1))
                    .isInstanceOf(ReservaException.class)
                    .hasMessage("Indicá el producto (productoId) o su nombre y precio.");
        }
    }
}
