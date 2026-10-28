package com.LosCiruelos.padel_club_api.reservas.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.MANIANA;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.mapper.ConfiguracionMapper;
import com.LosCiruelos.padel_club_api.mapper.CanchaMapper;
import com.LosCiruelos.padel_club_api.club.service.CanchaService;
import com.LosCiruelos.padel_club_api.usuarios.service.ClienteProfileService;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.mapper.PagoMapper;
import com.LosCiruelos.padel_club_api.reservas.dto.request.JugadorRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.request.PartidoRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.response.JugadorResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.HorarioOcupadoException;
import com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Exceptions.UsuarioNotFoundException;
import com.LosCiruelos.padel_club_api.club.repository.CanchaRepository;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijo;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijoJugador;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.TipoReserva;
import com.LosCiruelos.padel_club_api.mapper.ReservaMapper;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnoFijoRepository;

/** Partidos de 4 jugadores en ReservaService (alta, modificación y los de un turno fijo), con los repositorios simulados. */
class ReservaServicePartidosTest {

    private ReservaRepository reservaRepository;
    private TurnoFijoRepository turnoFijoRepository;
    private UsuarioService usuarioService;
    private ReservaService reservas;

    private Usuario admin;
    private Usuario empleado;
    private Usuario cliente;
    private Usuario otroCliente;

    @BeforeEach
    void setUp() {
        ConfiguracionRepository configuracionRepository = mock(ConfiguracionRepository.class);
        when(configuracionRepository.obtener()).thenReturn(TestData.configuracion());
        ConfiguracionService configuracionService = new ConfiguracionService(configuracionRepository, new ConfiguracionMapper());

        reservaRepository = mock(ReservaRepository.class);
        turnoFijoRepository = mock(TurnoFijoRepository.class);
        CanchaRepository canchaRepository = mock(CanchaRepository.class);
        usuarioService = mock(UsuarioService.class);
        StockService stockService = mock(StockService.class);

        ReservaMapper mapper = new ReservaMapper(configuracionService, new PoliticaCancelacion(configuracionService), TestData.reloj(AHORA), new PagoMapper());
        CanchaService canchaService = new CanchaService(canchaRepository, new CanchaMapper());
        DisponibilidadService disponibilidad = new DisponibilidadService(configuracionService, canchaService, reservaRepository,
                turnoFijoRepository, TestData.reloj(AHORA));
        reservas = new ReservaService(configuracionService, usuarioService,
                mock(ClienteProfileService.class), reservaRepository, canchaService, mock(PagoRepository.class),
                stockService, mapper, disponibilidad, new JugadoresService(usuarioService), turnoFijoRepository,
                TestData.reloj(AHORA));

        admin = usuario(3L, Role.ADMIN);
        empleado = usuario(4L, Role.EMPLEADO);
        cliente = usuario(1L, Role.CLIENTE);
        otroCliente = usuario(2L, Role.CLIENTE);
        for (Usuario u : List.of(admin, empleado, cliente, otroCliente)) {
            when(usuarioService.autenticado(u.getEmail())).thenReturn(u);
            when(usuarioService.findByIdOrThrow(u.getId())).thenReturn(u);
        }
        when(usuarioService.findByIdOrThrow(99L)).thenThrow(new UsuarioNotFoundException("Usuario no encontrado: 99"));
        when(canchaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(TestData.cancha()));
        when(reservaRepository.saveAndFlush(any(Reserva.class))).thenAnswer(inv -> {
            Reserva r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(60L);
            }
            return r;
        });
    }

    private static PartidoRequest pedido(LocalDate fecha, String inicio, String fin, JugadorRequest... jugadores) {
        PartidoRequest req = new PartidoRequest();
        req.setCanchaId(1L);
        req.setFechaReserva(fecha);
        req.setHoraInicio(LocalTime.parse(inicio));
        req.setHoraFin(LocalTime.parse(fin));
        req.setJugadores(new ArrayList<>(List.of(jugadores)));
        return req;
    }

    private PartidoRequest pedidoBasico() {
        return pedido(MANIANA, "18:00", "19:30", JugadorRequest.registrado(1L), JugadorRequest.registrado(2L),
                JugadorRequest.invitado("Juan Invitado"), new JugadorRequest(null, "  Pedro  ", "11 5555-0000"));
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Crear {

        @Test
        void quedaConfirmadoSinVencimientoConLosCuatroJugadoresYElAdminComoTitular() {
            ReservaResponse r = reservas.crearPartido(pedidoBasico(), admin.getEmail());

            assertThat(r.getTipo()).isEqualTo(TipoReserva.PARTIDO);
            assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
            assertThat(r.getExpiresAt()).isNull();
            assertThat(r.getClienteId()).isEqualTo(admin.getId());
            assertThat(r.getMontoTotal()).isEqualByComparingTo("15000");
            assertThat(r.getMontoPendiente()).isEqualByComparingTo("15000");
            assertThat(r.getJugadores()).extracting(JugadorResponse::getPosicion).containsExactly(1, 2, 3, 4);
            assertThat(r.getJugadores()).extracting(JugadorResponse::getUsuarioId).containsExactly(1L, 2L, null, null);
            assertThat(r.getJugadores()).extracting(JugadorResponse::getNombre)
                    .containsExactly("Nombre1 Apellido1", "Nombre2 Apellido2", "Juan Invitado", "Pedro");
            assertThat(r.getJugadores()).extracting(JugadorResponse::getInvitado)
                    .containsExactly(false, false, true, true);
            // cada uno debe su parte: 15000 / 4
            assertThat(r.getJugadores()).extracting(JugadorResponse::getParte)
                    .allSatisfy(parte -> assertThat(parte).isEqualByComparingTo("3750"));
            assertThat(r.getJugadores()).extracting(JugadorResponse::getDebe)
                    .allSatisfy(debe -> assertThat(debe).isEqualByComparingTo("3750"));
        }

        @Test
        void soloElAdmin() {
            assertThatThrownBy(() -> reservas.crearPartido(pedidoBasico(), empleado.getEmail()))
                    .isInstanceOf(PermisoDenegadoException.class)
                    .hasMessage("Solo el ADMIN puede armar partidos.");
            verify(reservaRepository, never()).saveAndFlush(any());
        }

        @Test
        void validaLosJugadores() {
            PartidoRequest tres = pedidoBasico();
            tres.getJugadores().remove(3);
            assertThatThrownBy(() -> reservas.crearPartido(tres, admin.getEmail()))
                    .isInstanceOf(ReservaException.class).hasMessage("Un partido tiene 4 jugadores.");

            PartidoRequest repetido = pedido(MANIANA, "18:00", "19:30", JugadorRequest.registrado(1L),
                    JugadorRequest.registrado(1L), JugadorRequest.invitado("A B"), JugadorRequest.invitado("C D"));
            assertThatThrownBy(() -> reservas.crearPartido(repetido, admin.getEmail()))
                    .hasMessage("El mismo cliente no puede estar dos veces en el partido.");

            PartidoRequest sinNombre = pedido(MANIANA, "18:00", "19:30", JugadorRequest.registrado(1L),
                    JugadorRequest.invitado("  "), JugadorRequest.invitado("A B"), JugadorRequest.invitado("C D"));
            assertThatThrownBy(() -> reservas.crearPartido(sinNombre, admin.getEmail()))
                    .hasMessage("Indicá el cliente (usuarioId) o el nombre del jugador 2.");

            PartidoRequest noCliente = pedido(MANIANA, "18:00", "19:30", JugadorRequest.registrado(4L),
                    JugadorRequest.invitado("X Y"), JugadorRequest.invitado("A B"), JugadorRequest.invitado("C D"));
            assertThatThrownBy(() -> reservas.crearPartido(noCliente, admin.getEmail()))
                    .hasMessage("El jugador 1 no es un cliente.");

            PartidoRequest inexistente = pedido(MANIANA, "18:00", "19:30", JugadorRequest.registrado(99L),
                    JugadorRequest.invitado("X Y"), JugadorRequest.invitado("A B"), JugadorRequest.invitado("C D"));
            assertThatThrownBy(() -> reservas.crearPartido(inexistente, admin.getEmail()))
                    .hasMessage("El jugador 1 no existe (usuario 99).");

            otroCliente.setEnabled(false);
            assertThatThrownBy(() -> reservas.crearPartido(pedidoBasico(), admin.getEmail()))
                    .hasMessage("La cuenta del jugador 2 está desactivada.");
            verify(reservaRepository, never()).saveAndFlush(any());
        }

        @Test
        void unHorarioOcupadoNoSePuedeUsar() {
            Reserva otra = TestData.reserva(7L, cliente, EstadoReserva.SEÑADA, "15000");
            when(reservaRepository.findQueOcupan(eq(1L), eq(MANIANA), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(otra));

            assertThatThrownBy(() -> reservas.crearPartido(pedidoBasico(), admin.getEmail()))
                    .isInstanceOf(HorarioOcupadoException.class);
        }

        @Test
        void unTurnoFijoBloqueaElHorario() {
            TurnoFijo tf = TurnoFijo.builder().id(8L).build();
            when(turnoFijoRepository.findQueBloquean(eq(1L), eq(MANIANA.getDayOfWeek()), eq(MANIANA), any(), any()))
                    .thenReturn(List.of(tf));

            assertThatThrownBy(() -> reservas.crearPartido(pedidoBasico(), admin.getEmail()))
                    .isInstanceOf(HorarioOcupadoException.class)
                    .hasMessage("Ese horario es un turno fijo del club en esa cancha.");
        }

        @Test
        void validaElHorarioComoUnaReservaNormal() {
            assertThatThrownBy(() -> reservas.crearPartido(pedido(AHORA.toLocalDate(), "09:00", "10:30",
                    pedidoBasico().getJugadores().toArray(JugadorRequest[]::new)), admin.getEmail()))
                    .hasMessage("No podés reservar un horario que ya pasó.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Actualizar {

        private Reserva partido;

        @BeforeEach
        void partidoExistente() {
            partido = TestData.partido(60L, admin, cliente, "15000");
            when(reservaRepository.findByIdForUpdate(60L)).thenReturn(Optional.of(partido));
        }

        private PartidoRequest mismosDatos(JugadorRequest... jugadores) {
            return pedido(partido.getFechaReserva(), "18:00", "19:30", jugadores);
        }

        @Test
        void reemplazaAUnJugadorQueNoPagoEnSuLugar() {
            ReservaResponse r = reservas.actualizarPartido(60L, mismosDatos(JugadorRequest.registrado(1L),
                    JugadorRequest.registrado(2L), JugadorRequest.invitado("Invitado 3"),
                    JugadorRequest.invitado("Invitado 4")), admin.getEmail());

            assertThat(r.getJugadores()).extracting(JugadorResponse::getId).containsExactly(201L, 202L, 203L, 204L);
            assertThat(r.getJugadores().get(1).getUsuarioId()).isEqualTo(2L);
            assertThat(r.getJugadores().get(1).getInvitado()).isFalse();
            assertThat(r.getMontoTotal()).isEqualByComparingTo("15000");
        }

        @Test
        void noSePuedeReemplazarAUnJugadorQueYaPago() {
            Pago pago = TestData.pago(1L, partido, EstadoPago.APROBADO, "3750");
            pago.setProvider(PaymentProvider.MANUAL);
            pago.setJugador(partido.getJugadores().get(1));

            assertThatThrownBy(() -> reservas.actualizarPartido(60L, mismosDatos(JugadorRequest.registrado(1L),
                    JugadorRequest.registrado(2L), JugadorRequest.invitado("Invitado 3"),
                    JugadorRequest.invitado("Invitado 4")), admin.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("Invitado 2 ya pagó o tiene una bonificación: no se lo puede reemplazar.");

            // al mismo jugador (mismo nombre de invitado) sí se le puede cambiar el teléfono
            ReservaResponse r = reservas.actualizarPartido(60L, mismosDatos(JugadorRequest.registrado(1L),
                    new JugadorRequest(null, "invitado 2", "11 4444-0000"), JugadorRequest.invitado("Invitado 3"),
                    JugadorRequest.invitado("Invitado 4")), admin.getEmail());
            assertThat(r.getJugadores().get(1).getTelefono()).isEqualTo("11 4444-0000");
            // con 3750 pagado (menos que la seña) queda parcial
            assertThat(r.getEstado()).isEqualTo(EstadoReserva.PARCIALMENTE_PAGADA);
        }

        @Test
        void cambiarElHorarioRecalculaElPrecioYNoPuedeQuedarPorDebajoDeLoPagado() {
            ReservaResponse r = reservas.actualizarPartido(60L, pedido(partido.getFechaReserva(), "18:00", "21:00",
                    partido.getJugadores().stream().map(j -> j.getUsuario() != null
                            ? JugadorRequest.registrado(j.getUsuario().getId())
                            : JugadorRequest.invitado(j.getNombre())).toArray(JugadorRequest[]::new)),
                    admin.getEmail());
            assertThat(r.getMontoTotal()).isEqualByComparingTo("30000");
            assertThat(r.getHoraFin()).isEqualTo(LocalTime.of(21, 0));

            TestData.pago(1L, partido, EstadoPago.APROBADO, "20000").setJugador(partido.getJugadores().get(0));
            assertThatThrownBy(() -> reservas.actualizarPartido(60L, mismosDatos(JugadorRequest.registrado(1L),
                    JugadorRequest.invitado("Invitado 2"), JugadorRequest.invitado("Invitado 3"),
                    JugadorRequest.invitado("Invitado 4")), admin.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("El total nuevo ($15000.00) es menor que lo ya pagado ($20000).");
        }

        @Test
        void unPartidoDeUnTurnoFijoNoCambiaDeFecha() {
            partido.setTurnoFijo(TurnoFijo.builder().id(8L).build());

            assertThatThrownBy(() -> reservas.actualizarPartido(60L, pedido(partido.getFechaReserva().plusDays(1), "18:00",
                    "19:30", JugadorRequest.registrado(1L), JugadorRequest.invitado("Invitado 2"),
                    JugadorRequest.invitado("Invitado 3"), JugadorRequest.invitado("Invitado 4")), admin.getEmail()))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("no cambia de fecha");
        }

        @Test
        void soloPartidosConfirmadosYQueNoSeJugaron() {
            Reserva normal = TestData.reserva(61L, cliente, EstadoReserva.SEÑADA, "15000");
            when(reservaRepository.findByIdForUpdate(61L)).thenReturn(Optional.of(normal));
            assertThatThrownBy(() -> reservas.actualizarPartido(61L, pedidoBasico(), admin.getEmail()))
                    .hasMessage("La reserva #61 no es un partido.");

            partido.setEstado(EstadoReserva.CANCELADA);
            assertThatThrownBy(() -> reservas.actualizarPartido(60L, pedidoBasico(), admin.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("No se puede modificar un partido CANCELADA.");

            partido.setEstado(EstadoReserva.CONFIRMADA);
            partido.setFechaReserva(AHORA.toLocalDate().minusDays(1));
            assertThatThrownBy(() -> reservas.actualizarPartido(60L, pedidoBasico(), admin.getEmail()))
                    .hasMessage("El partido ya se jugó: no se puede modificar.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class TurnoFijoSemanal {

        private TurnoFijo tf;

        @BeforeEach
        void turnoFijo() {
            tf = TurnoFijo.builder().id(8L).cancha(TestData.cancha()).diaSemana(MANIANA.getDayOfWeek())
                    .horaInicio(LocalTime.of(20, 0)).horaFin(LocalTime.of(21, 30)).desde(MANIANA)
                    .cantidadPelotas(0).cantidadPaletas(0).activo(true).createdBy(admin).createdAt(AHORA).build();
            tf.getJugadores().add(TurnoFijoJugador.builder().turnoFijo(tf).posicion(1).usuario(cliente).build());
            for (int posicion = 2; posicion <= 4; posicion++) {
                tf.getJugadores().add(TurnoFijoJugador.builder().turnoFijo(tf).posicion(posicion)
                        .nombre("Fijo " + posicion).build());
            }
            when(turnoFijoRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(tf));
        }

        @Test
        void creaElPartidoDeEsaFechaConLosJugadoresDelTurnoFijo() {
            // el propio turno fijo todavía no tiene partido ese día: no se bloquea a sí mismo
            when(turnoFijoRepository.findQueBloquean(anyLong(), any(), any(), any(), any())).thenReturn(List.of(tf));

            assertThat(reservas.crearPartidoDeTurnoFijo(8L, MANIANA)).contains(60L);

            org.mockito.ArgumentCaptor<Reserva> guardada = org.mockito.ArgumentCaptor.forClass(Reserva.class);
            verify(reservaRepository).saveAndFlush(guardada.capture());
            Reserva r = guardada.getValue();
            assertThat(r.getTurnoFijo()).isSameAs(tf);
            assertThat(r.getCliente()).isSameAs(admin);
            assertThat(r.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
            assertThat(r.getJugadores()).hasSize(4);
            assertThat(r.getJugadores().get(0).getUsuario()).isSameAs(cliente);
            assertThat(r.getMontoTotal()).isEqualByComparingTo("15000");
        }

        @Test
        void noHaceNadaSiYaTienePartidoSiNoSeJuegaEseDiaOSiEstaDadoDeBaja() {
            when(reservaRepository.existsByTurnoFijoAndFechaReserva(tf, MANIANA)).thenReturn(true);
            assertThat(reservas.crearPartidoDeTurnoFijo(8L, MANIANA)).isEmpty();

            assertThat(reservas.crearPartidoDeTurnoFijo(8L, MANIANA.plusDays(1))).isEmpty();
            assertThat(reservas.crearPartidoDeTurnoFijo(8L, MANIANA.minusWeeks(1))).isEmpty();

            tf.setActivo(false);
            assertThat(reservas.crearPartidoDeTurnoFijo(8L, MANIANA.plusWeeks(1))).isEmpty();
            verify(reservaRepository, never()).saveAndFlush(any());
        }

        @Test
        void unHorarioOcupadoPorOtraReservaLanzaLaExcepcion() {
            when(reservaRepository.findQueOcupan(eq(1L), eq(MANIANA), any(), any(), any(), any(), any(), any()))
                    .thenReturn(List.of(TestData.reserva(7L, cliente, EstadoReserva.SEÑADA, "15000")));

            assertThatThrownBy(() -> reservas.crearPartidoDeTurnoFijo(8L, MANIANA))
                    .isInstanceOf(HorarioOcupadoException.class);
        }
    }

    @Test
    void laParteDeCadaJugadorRepartiendoLosCentavos() {
        Reserva partido = TestData.partido(60L, admin, cliente, "10000.01");

        assertThat(partido.parteDe(1)).isEqualByComparingTo("2500.00");
        assertThat(partido.parteDe(3)).isEqualByComparingTo("2500.00");
        assertThat(partido.parteDe(4)).isEqualByComparingTo("2500.01");
        assertThat(partido.parteDe(1).multiply(BigDecimal.valueOf(3)).add(partido.parteDe(4)))
                .isEqualByComparingTo("10000.01");
    }
}
