package com.LosCiruelos.padel_club_api.Services;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.reserva;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.DTOs.Admin.FiltroReservasAdmin;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ListadoReservasResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ListadoReservasResponse.Totales;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ReservaResponse;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.BusquedaReservasAdmin;

class ReservaAdminServiceTest {

    private static final LocalDate HOY = AHORA.toLocalDate();
    private static final Totales TOTALES = new Totales(57, new BigDecimal("855000.00"), new BigDecimal("690000.00"),
            new BigDecimal("410000.00"), new BigDecimal("280000.00"), new BigDecimal("0.00"), new BigDecimal("165000.00"));

    private BusquedaReservasAdmin busqueda;
    private ReservaService reservaService;
    private ReservaAdminService listado;
    private Reserva unaReserva;

    @BeforeEach
    void setUp() {
        busqueda = mock(BusquedaReservasAdmin.class);
        reservaService = mock(ReservaService.class);
        listado = new ReservaAdminService(busqueda, reservaService, TestData.reloj(AHORA));

        unaReserva = reserva(10L, usuario(1L, Role.CLIENTE), EstadoReserva.SEÑADA, "15000");
        when(busqueda.buscar(any(), any())).thenAnswer(inv -> new PageImpl<>(List.of(unaReserva),
                inv.<Pageable>getArgument(1), 57));
        when(busqueda.totales(any())).thenReturn(TOTALES);
        ReservaResponse respuesta = new ReservaResponse();
        respuesta.setId(10L);
        when(reservaService.aResponse(unaReserva)).thenReturn(respuesta);
    }

    private FiltroReservasAdmin filtro() {
        ArgumentCaptor<FiltroReservasAdmin> captor = ArgumentCaptor.forClass(FiltroReservasAdmin.class);
        org.mockito.Mockito.verify(busqueda).buscar(captor.capture(), any());
        return captor.getValue();
    }

    private Pageable pedido() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        org.mockito.Mockito.verify(busqueda).buscar(any(), captor.capture());
        return captor.getValue();
    }

    @Test
    void devuelveLaPaginaConLosTotalesDelFiltroCompleto() {
        ListadoReservasResponse r = listado.listar(null, null, null, null, null, null, null, false, 2, 10, null);

        assertThat(r.contenido()).extracting(ReservaResponse::getId).containsExactly(10L);
        assertThat(r.pagina()).isEqualTo(2);
        assertThat(r.tamanio()).isEqualTo(10);
        assertThat(r.totalElementos()).isEqualTo(57);
        assertThat(r.totalPaginas()).isEqualTo(6);
        assertThat(r.totales()).isEqualTo(TOTALES);
    }

    @Test
    void sinFiltrosNoFiltraNadaYElTamanioPorDefectoEs20() {
        listado.listar(null, null, null, null, null, null, null, false, 0, null, null);

        assertThat(filtro()).isEqualTo(new FiltroReservasAdmin(null, null, null, null, null, null, null));
        assertThat(pedido().getPageSize()).isEqualTo(20);
    }

    @Test
    void pasaTodosLosFiltros() {
        listado.listar(HOY, HOY.plusDays(30), List.of(EstadoReserva.PAGADA, EstadoReserva.CANCELADA), 7L,
                "  Pérez ", 2L, null, false, 0, 50, null);

        assertThat(filtro()).isEqualTo(new FiltroReservasAdmin(HOY, HOY.plusDays(30),
                Set.of(EstadoReserva.PAGADA, EstadoReserva.CANCELADA), 7L, "perez", 2L, null));
    }

    @Test
    void pendientesSonLasQueDebenPlataHayanJugadoONo() {
        listado.listar(null, null, null, null, null, null, null, true, 0, null, null);

        // CONFIRMADA: partido del ADMIN sin nada pagado
        assertThat(filtro().estados()).containsExactlyInAnyOrder(EstadoReserva.CONFIRMADA, EstadoReserva.SEÑADA,
                EstadoReserva.PARCIALMENTE_PAGADA);
    }

    @Test
    void pendientesSeCombinaConElEstado() {
        assertThat(ReservaAdminService.estadosEfectivos(List.of(EstadoReserva.SEÑADA, EstadoReserva.PAGADA), true))
                .containsExactly(EstadoReserva.SEÑADA);
        // sin coincidencia no hay resultados (no "todos")
        assertThat(ReservaAdminService.estadosEfectivos(List.of(EstadoReserva.PENDIENTE), true)).isEmpty();
        assertThat(ReservaAdminService.estadosEfectivos(List.of(), false)).isNull();
    }

    @Test
    void elOrdenPorDefectoEsAscendenteParaLasProximasYDescendenteParaElHistorial() {
        listado.listar(HOY, null, null, null, null, null, null, false, 0, null, null);
        assertThat(pedido().getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "fechaReserva", "horaInicio")
                .and(Sort.by(Sort.Direction.ASC, "id")));
    }

    @Test
    void historialSinDesdeODesdeElPasadoEsDescendente() {
        listado.listar(HOY.minusDays(1), HOY.plusDays(1), null, null, null, null, null, false, 0, null, null);
        assertThat(pedido().getSort().getOrderFor("fechaReserva").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void sinFechasTambienEsDescendente() {
        listado.listar(null, null, null, null, null, null, null, false, 0, null, null);
        assertThat(pedido().getSort().getOrderFor("fechaReserva").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void elOrdenSePuedeElegir() {
        listado.listar(null, null, null, null, null, null, null, false, 0, null, "ASC");
        assertThat(pedido().getSort().getOrderFor("horaInicio").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void validaLosParametros() {
        assertThatThrownBy(() -> listado.listar(HOY, HOY.minusDays(1), null, null, null, null, null, false, 0, null, null))
                .isInstanceOf(ReservaException.class).hasMessage("La fecha desde no puede ser posterior a hasta.");
        assertThatThrownBy(() -> listado.listar(null, null, null, null, null, null, null, false, 0, 101, null))
                .isInstanceOf(ReservaException.class).hasMessageContaining("entre 1 y 100");
        assertThatThrownBy(() -> listado.listar(null, null, null, null, null, null, null, false, 0, 0, null))
                .isInstanceOf(ReservaException.class);
        assertThatThrownBy(() -> listado.listar(null, null, null, null, null, null, null, false, -1, null, null))
                .isInstanceOf(ReservaException.class);
        assertThatThrownBy(() -> listado.listar(null, null, null, null, null, null, null, false, 0, null, "fecha"))
                .isInstanceOf(ReservaException.class).hasMessageContaining("'asc' o 'desc'");
        verifyNoInteractions(busqueda);
    }

    @Test
    void elTextoDelClienteSeNormaliza() {
        assertThat(com.LosCiruelos.padel_club_api.Specifications.Busquedas.normalizar("  JOSÉ   María ")).isEqualTo("jose maria");
        assertThat(com.LosCiruelos.padel_club_api.Specifications.Busquedas.normalizar("  ")).isNull();
        assertThat(com.LosCiruelos.padel_club_api.Specifications.Busquedas.contiene("50%_a\\b")).isEqualTo("%50\\%\\_a\\\\b%");
    }
}
