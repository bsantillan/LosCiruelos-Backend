package com.LosCiruelos.padel_club_api.reservas.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.HOY;
import static com.LosCiruelos.padel_club_api.TestData.reserva;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PaginaResponse;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Entities.Enum.TipoProducto;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaNoEncontradaException;
import com.LosCiruelos.padel_club_api.Repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.Services.ConfiguracionService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;
import com.LosCiruelos.padel_club_api.mapper.PagoMapper;
import com.LosCiruelos.padel_club_api.mapper.ReservaMapper;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ListadoReservasResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ListadoReservasResponse.Totales;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.repository.FiltroReservas;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;

class ReservaConsultaServiceTest {

    private ReservaRepository reservaRepository;
    private ReservaMapper mapper;
    private ReservaConsultaService consultas;

    private Usuario cliente;
    private Usuario otroCliente;

    @BeforeEach
    void setUp() {
        ConfiguracionRepository configuracionRepository = mock(ConfiguracionRepository.class);
        when(configuracionRepository.obtener()).thenReturn(TestData.configuracion());
        ConfiguracionService configuracionService = new ConfiguracionService(configuracionRepository);
        UsuarioService usuarioService = mock(UsuarioService.class);
        reservaRepository = mock(ReservaRepository.class);
        mapper = new ReservaMapper(configuracionService, new PoliticaCancelacion(configuracionService),
                TestData.reloj(AHORA), new PagoMapper());
        consultas = new ReservaConsultaService(usuarioService, reservaRepository, mapper, TestData.reloj(AHORA));

        cliente = usuario(1L, Role.CLIENTE);
        otroCliente = usuario(2L, Role.CLIENTE);
        for (Usuario u : List.of(cliente, otroCliente)) {
            when(usuarioService.autenticado(u.getEmail())).thenReturn(u);
        }
    }

    @Test
    void obtenerUnaReservaAjenaDevuelveNoEncontrada() {
        Reserva ajena = reserva(11L, otroCliente, EstadoReserva.PENDIENTE, "15000");
        when(reservaRepository.findById(11L)).thenReturn(Optional.of(ajena));

        assertThatThrownBy(() -> consultas.obtener(11L, cliente.getEmail()))
                .isInstanceOf(ReservaNoEncontradaException.class);
        assertThat(consultas.obtener(11L, otroCliente.getEmail()).getId()).isEqualTo(11L);
    }

    @Test
    void listaLasDevolucionesPendientes() {
        Reserva jugada = reserva(20L, cliente, EstadoReserva.PAGADA, "21000");
        jugada.setFechaReserva(HOY);
        when(reservaRepository.findConAlquileresSinDevolver(eq(TipoProducto.ALQUILER),
                eq(Reserva.ESTADOS_QUE_SE_JUEGAN), eq(HOY), eq(AHORA.toLocalTime()))).thenReturn(List.of(jugada));

        assertThat(consultas.devolucionesPendientes()).extracting(ReservaResponse::getId).containsExactly(20L);
    }

    @Test
    void listaLosTurnosJugadosQueDebenSaldo() {
        Reserva jugada = reserva(30L, cliente, EstadoReserva.PARCIALMENTE_PAGADA, "15000");
        jugada.setFechaReserva(HOY.minusDays(1));
        when(reservaRepository.findTerminadasConSaldo(Reserva.ESTADOS_CON_SALDO, HOY, AHORA.toLocalTime()))
                .thenReturn(List.of(jugada));

        assertThat(consultas.saldosPendientes()).extracting(ReservaResponse::getId).containsExactly(30L);
    }

    @Test
    void unaReservaJugadaSinPagarDelTodoDebeSaldo() {
        Reserva jugada = reserva(30L, cliente, EstadoReserva.SEÑADA, "15000");
        jugada.setFechaReserva(HOY.minusDays(1));
        Reserva futura = reserva(31L, cliente, EstadoReserva.SEÑADA, "15000");
        Reserva pagaYJugada = reserva(32L, cliente, EstadoReserva.COMPLETADA, "15000");
        pagaYJugada.setFechaReserva(HOY.minusDays(1));

        ReservaResponse r = mapper.aResponse(jugada);
        assertThat(r.getTurnoTerminado()).isTrue();
        assertThat(r.getDebeSaldo()).isTrue();

        assertThat(mapper.aResponse(futura).getDebeSaldo()).isFalse();
        assertThat(mapper.aResponse(pagaYJugada).getTurnoTerminado()).isTrue();
        assertThat(mapper.aResponse(pagaYJugada).getDebeSaldo()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class MisReservas {

        @BeforeEach
        void unaPagina() {
            when(reservaRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Reserva>>any(),
                    any(Pageable.class))).thenAnswer(inv -> {
                        Pageable p = inv.getArgument(1);
                        return new PageImpl<>(List.of(reserva(1L, cliente, EstadoReserva.PAGADA, "15000")), p, 37);
                    });
        }

        private Pageable pedido() {
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(reservaRepository).findAll(org.mockito.ArgumentMatchers.<Specification<Reserva>>any(),
                    captor.capture());
            return captor.getValue();
        }

        @Test
        void devuelveUnaPaginaConElTotal() {
            PaginaResponse<ReservaResponse> r = consultas.misReservas(cliente.getEmail(), "historial",
                    List.of(EstadoReserva.COMPLETADA), HOY.minusDays(30), HOY, false, 2, 10);

            assertThat(r.contenido()).hasSize(1);
            assertThat(r.pagina()).isEqualTo(2);
            assertThat(r.tamanio()).isEqualTo(10);
            assertThat(r.totalElementos()).isEqualTo(37);
            assertThat(r.totalPaginas()).isEqualTo(4);
            assertThat(pedido().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "fechaReserva", "horaInicio"));
        }

        @Test
        void lasProximasVanDeLaMasCercanaALaMasLejana() {
            consultas.misReservas(cliente.getEmail(), "PROXIMAS", null, null, null, false, 0, 50);

            assertThat(pedido().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "fechaReserva", "horaInicio"));
        }

        @Test
        void sinTipoOrdenaDeLaMasNuevaALaMasVieja() {
            consultas.misReservas(cliente.getEmail(), null, null, null, null, false, 0, 10);

            assertThat(pedido().getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "fechaReserva", "horaInicio"));
        }

        @Test
        void validaLosParametros() {
            assertThatThrownBy(() -> consultas.misReservas(cliente.getEmail(), null, null, null, null, false, -1, 10))
                    .isInstanceOf(ReservaException.class);
            assertThatThrownBy(() -> consultas.misReservas(cliente.getEmail(), null, null, null, null, false, 0, 51))
                    .isInstanceOf(ReservaException.class);
            assertThatThrownBy(() -> consultas.misReservas(cliente.getEmail(), null, null, null, null, false, 0, 0))
                    .isInstanceOf(ReservaException.class);
            assertThatThrownBy(() -> consultas.misReservas(cliente.getEmail(), null, null, HOY, HOY.minusDays(1),
                    false, 0, 10))
                    .isInstanceOf(ReservaException.class)
                    .hasMessage("La fecha desde no puede ser posterior a hasta.");
            assertThatThrownBy(() -> consultas.misReservas(cliente.getEmail(), "futuras", null, null, null, false, 0, 10))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("proximas");
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class ListadoDelPersonal {

        private static final Totales TOTALES = new Totales(57, new BigDecimal("855000.00"),
                new BigDecimal("690000.00"), new BigDecimal("410000.00"), new BigDecimal("280000.00"),
                new BigDecimal("0.00"), new BigDecimal("165000.00"));

        @BeforeEach
        void unaPagina() {
            Reserva unaReserva = reserva(10L, usuario(1L, Role.CLIENTE), EstadoReserva.SEÑADA, "15000");
            when(reservaRepository.findAll(org.mockito.ArgumentMatchers.<Specification<Reserva>>any(),
                    any(Pageable.class))).thenAnswer(inv -> new PageImpl<>(List.of(unaReserva),
                            inv.<Pageable>getArgument(1), 57));
            when(reservaRepository.totalesParaPersonal(any())).thenReturn(TOTALES);
        }

        private FiltroReservas filtro() {
            ArgumentCaptor<FiltroReservas> captor = ArgumentCaptor.forClass(FiltroReservas.class);
            verify(reservaRepository).totalesParaPersonal(captor.capture());
            return captor.getValue();
        }

        private Pageable pedido() {
            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(reservaRepository).findAll(org.mockito.ArgumentMatchers.<Specification<Reserva>>any(),
                    captor.capture());
            return captor.getValue();
        }

        @Test
        void devuelveLaPaginaConLosTotalesDelFiltroCompleto() {
            ListadoReservasResponse r = consultas.listar(null, null, null, null, null, null, null, false, 2, 10, null);

            assertThat(r.contenido()).extracting(ReservaResponse::getId).containsExactly(10L);
            assertThat(r.pagina()).isEqualTo(2);
            assertThat(r.tamanio()).isEqualTo(10);
            assertThat(r.totalElementos()).isEqualTo(57);
            assertThat(r.totalPaginas()).isEqualTo(6);
            assertThat(r.totales()).isEqualTo(TOTALES);
        }

        @Test
        void sinFiltrosNoFiltraNadaYElTamanioPorDefectoEs20() {
            consultas.listar(null, null, null, null, null, null, null, false, 0, null, null);

            assertThat(filtro()).isEqualTo(new FiltroReservas(null, null, null, null, null, null, null));
            assertThat(pedido().getPageSize()).isEqualTo(20);
        }

        @Test
        void pasaTodosLosFiltros() {
            consultas.listar(HOY, HOY.plusDays(30), List.of(EstadoReserva.PAGADA, EstadoReserva.CANCELADA), 7L,
                    "  Pérez ", 2L, null, false, 0, 50, null);

            assertThat(filtro()).isEqualTo(new FiltroReservas(HOY, HOY.plusDays(30),
                    Set.of(EstadoReserva.PAGADA, EstadoReserva.CANCELADA), 7L, "perez", 2L, null));
        }

        @Test
        void pendientesSonLasQueDebenPlataHayanJugadoONo() {
            consultas.listar(null, null, null, null, null, null, null, true, 0, null, null);

            // CONFIRMADA: partido del ADMIN sin nada pagado
            assertThat(filtro().estados()).containsExactlyInAnyOrder(EstadoReserva.CONFIRMADA, EstadoReserva.SEÑADA,
                    EstadoReserva.PARCIALMENTE_PAGADA);
        }

        @Test
        void pendientesSeCombinaConElEstado() {
            assertThat(ReservaConsultaService.estadosEfectivos(List.of(EstadoReserva.SEÑADA, EstadoReserva.PAGADA), true))
                    .containsExactly(EstadoReserva.SEÑADA);
            // sin coincidencia no hay resultados (no "todos")
            assertThat(ReservaConsultaService.estadosEfectivos(List.of(EstadoReserva.PENDIENTE), true)).isEmpty();
            assertThat(ReservaConsultaService.estadosEfectivos(List.of(), false)).isNull();
        }

        @Test
        void elOrdenPorDefectoEsAscendenteParaLasProximasYDescendenteParaElHistorial() {
            consultas.listar(HOY, null, null, null, null, null, null, false, 0, null, null);
            assertThat(pedido().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "fechaReserva", "horaInicio")
                    .and(Sort.by(Sort.Direction.ASC, "id")));
        }

        @Test
        void historialSinDesdeODesdeElPasadoEsDescendente() {
            consultas.listar(HOY.minusDays(1), HOY.plusDays(1), null, null, null, null, null, false, 0, null, null);
            assertThat(pedido().getSort().getOrderFor("fechaReserva").getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        void sinFechasTambienEsDescendente() {
            consultas.listar(null, null, null, null, null, null, null, false, 0, null, null);
            assertThat(pedido().getSort().getOrderFor("fechaReserva").getDirection()).isEqualTo(Sort.Direction.DESC);
        }

        @Test
        void elOrdenSePuedeElegir() {
            consultas.listar(null, null, null, null, null, null, null, false, 0, null, "ASC");
            assertThat(pedido().getSort().getOrderFor("horaInicio").getDirection()).isEqualTo(Sort.Direction.ASC);
        }

        @Test
        void validaLosParametros() {
            assertThatThrownBy(() -> consultas.listar(HOY, HOY.minusDays(1), null, null, null, null, null, false, 0, null, null))
                    .isInstanceOf(ReservaException.class).hasMessage("La fecha desde no puede ser posterior a hasta.");
            assertThatThrownBy(() -> consultas.listar(null, null, null, null, null, null, null, false, 0, 101, null))
                    .isInstanceOf(ReservaException.class).hasMessageContaining("entre 1 y 100");
            assertThatThrownBy(() -> consultas.listar(null, null, null, null, null, null, null, false, 0, 0, null))
                    .isInstanceOf(ReservaException.class);
            assertThatThrownBy(() -> consultas.listar(null, null, null, null, null, null, null, false, -1, null, null))
                    .isInstanceOf(ReservaException.class);
            assertThatThrownBy(() -> consultas.listar(null, null, null, null, null, null, null, false, 0, null, "fecha"))
                    .isInstanceOf(ReservaException.class).hasMessageContaining("'asc' o 'desc'");
            verifyNoInteractions(reservaRepository);
        }
    }
}
