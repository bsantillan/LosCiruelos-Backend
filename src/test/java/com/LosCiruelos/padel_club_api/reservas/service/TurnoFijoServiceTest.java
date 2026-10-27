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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.LosCiruelos.padel_club_api.Services.CanchaService;
import com.LosCiruelos.padel_club_api.Services.ClienteProfileService;
import com.LosCiruelos.padel_club_api.Services.ConfiguracionService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;
import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.mapper.PagoMapper;
import com.LosCiruelos.padel_club_api.mapper.TurnoFijoMapper;
import com.LosCiruelos.padel_club_api.pagos.service.PagoService;
import com.LosCiruelos.padel_club_api.reservas.dto.request.JugadorRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.request.TurnoFijoRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.response.TurnoFijoResponse;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.HorarioOcupadoException;
import com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Exceptions.TurnoFijoNoEncontradoException;
import com.LosCiruelos.padel_club_api.Repository.CanchaRepository;
import com.LosCiruelos.padel_club_api.Repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentServiceFactory;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijo;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijoJugador;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.OpcionReembolso;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.TipoReserva;
import com.LosCiruelos.padel_club_api.mapper.ReservaMapper;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnoFijoRepository;

/** Hoy es el martes 13/10/2026 a las 10:00. */
class TurnoFijoServiceTest {

    private TurnoFijoRepository turnoFijoRepository;
    private ReservaRepository reservaRepository;
    private CancelacionService cancelacionService;
    private StockService stockService;
    private TurnoFijoService turnos;

    private Usuario admin;
    private Usuario empleado;
    private Usuario cliente;

    @BeforeEach
    void setUp() {
        ConfiguracionRepository configuracionRepository = mock(ConfiguracionRepository.class);
        when(configuracionRepository.obtener()).thenReturn(TestData.configuracion());
        ConfiguracionService configuracionService = new ConfiguracionService(configuracionRepository);

        turnoFijoRepository = mock(TurnoFijoRepository.class);
        reservaRepository = mock(ReservaRepository.class);
        CanchaRepository canchaRepository = mock(CanchaRepository.class);
        UsuarioService usuarioService = mock(UsuarioService.class);
        stockService = mock(StockService.class);
        cancelacionService = mock(CancelacionService.class);

        ReservaMapper mapper = new ReservaMapper(configuracionService, new PoliticaCancelacion(configuracionService), TestData.reloj(AHORA), new PagoMapper());
        DisponibilidadService disponibilidad = new DisponibilidadService(configuracionService, new CanchaService(canchaRepository), reservaRepository,
                turnoFijoRepository, TestData.reloj(AHORA));
        ReservaService reservaService = new ReservaService(configuracionService, usuarioService,
                mock(ClienteProfileService.class), reservaRepository, canchaRepository, mock(PagoRepository.class),
                stockService, mapper, disponibilidad,
                TestData.reloj(AHORA));
        PagoService pagoService = new PagoService(mock(PaymentServiceFactory.class), reservaService,
                mock(PagoRepository.class), configuracionService, usuarioService, stockService,
                TestData.reloj(AHORA), mock(ApplicationEventPublisher.class), new PagoMapper());
        PartidoService partidoService = new PartidoService(reservaService, disponibilidad, mapper, pagoService, usuarioService,
                configuracionService, stockService, canchaRepository, reservaRepository, turnoFijoRepository,
                TestData.reloj(AHORA));
        turnos = new TurnoFijoService(turnoFijoRepository, reservaRepository, canchaRepository, reservaService,
                disponibilidad, mapper, new TurnoFijoMapper(), partidoService, cancelacionService, stockService, configuracionService, usuarioService,
                TestData.reloj(AHORA));

        admin = usuario(3L, Role.ADMIN);
        empleado = usuario(4L, Role.EMPLEADO);
        cliente = usuario(1L, Role.CLIENTE);
        for (Usuario u : List.of(admin, empleado, cliente)) {
            when(usuarioService.autenticado(u.getEmail())).thenReturn(u);
            when(usuarioService.findByIdOrThrow(u.getId())).thenReturn(u);
        }
        when(canchaRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(TestData.cancha()));
        when(turnoFijoRepository.save(any(TurnoFijo.class))).thenAnswer(inv -> {
            TurnoFijo tf = inv.getArgument(0);
            if (tf.getId() == null) {
                tf.setId(8L);
            }
            return tf;
        });
        when(reservaRepository.save(any(Reserva.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private static TurnoFijoRequest pedido(LocalDate desde, String inicio, String fin) {
        TurnoFijoRequest req = new TurnoFijoRequest();
        req.setCanchaId(1L);
        req.setDesde(desde);
        req.setHoraInicio(LocalTime.parse(inicio));
        req.setHoraFin(LocalTime.parse(fin));
        req.setJugadores(List.of(JugadorRequest.registrado(1L), JugadorRequest.invitado("Fijo 2"),
                JugadorRequest.invitado("Fijo 3"), JugadorRequest.invitado("Fijo 4")));
        req.setCantidadPelotas(2);
        return req;
    }

    /** Turno fijo activo de los miércoles de 20:00 a 21:30 desde mañana. */
    private TurnoFijo turnoFijo() {
        TurnoFijo tf = TurnoFijo.builder().id(8L).cancha(TestData.cancha()).diaSemana(DayOfWeek.WEDNESDAY)
                .horaInicio(LocalTime.of(20, 0)).horaFin(LocalTime.of(21, 30)).desde(MANIANA).cantidadPelotas(0)
                .cantidadPaletas(0).activo(true).createdBy(admin).createdAt(AHORA).build();
        tf.getJugadores().add(TurnoFijoJugador.builder().turnoFijo(tf).posicion(1).usuario(cliente).build());
        when(turnoFijoRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(tf));
        when(turnoFijoRepository.findById(8L)).thenReturn(Optional.of(tf));
        return tf;
    }

    private Reserva partidoDe(TurnoFijo tf, long id, LocalDate fecha) {
        Reserva partido = TestData.partido(id, admin, cliente, "15000");
        partido.setTurnoFijo(tf);
        partido.setFechaReserva(fecha);
        when(reservaRepository.findByIdForUpdate(id)).thenReturn(Optional.of(partido));
        return partido;
    }

    // ── Crear ────────────────────────────────────────────────────────────

    @Test
    void elDiaDeLaSemanaSaleDeLaFechaDesde() {
        assertThat(turnos.crear(pedido(MANIANA, "20:00", "21:30"), admin.getEmail())).isEqualTo(8L);

        ArgumentCaptor<TurnoFijo> guardado = ArgumentCaptor.forClass(TurnoFijo.class);
        verify(turnoFijoRepository).save(guardado.capture());
        TurnoFijo tf = guardado.getValue();
        assertThat(tf.getDiaSemana()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(tf.getActivo()).isTrue();
        assertThat(tf.getCantidadPelotas()).isEqualTo(2);
        assertThat(tf.getCantidadPaletas()).isZero();
        assertThat(tf.getCreatedBy()).isSameAs(admin);
        assertThat(tf.getJugadores()).extracting(TurnoFijoJugador::getPosicion).containsExactly(1, 2, 3, 4);
        assertThat(tf.getJugadores().get(0).getUsuario()).isSameAs(cliente);
        assertThat(tf.getJugadores().get(3).getNombre()).isEqualTo("Fijo 4");
    }

    @Test
    void validaElTurno() {
        assertThatThrownBy(() -> turnos.crear(pedido(AHORA.toLocalDate().minusDays(1), "20:00", "21:30"),
                admin.getEmail()))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La fecha desde tiene que ser hoy o en el futuro.");
        // el domingo el club cierra a las 20
        assertThatThrownBy(() -> turnos.crear(pedido(LocalDate.of(2026, 10, 18), "19:00", "20:30"),
                admin.getEmail()))
                .hasMessageContaining("cierre");
        assertThatThrownBy(() -> turnos.crear(pedido(MANIANA, "20:00", "21:30"), empleado.getEmail()))
                .isInstanceOf(PermisoDenegadoException.class);
        verify(turnoFijoRepository, never()).save(any());
    }

    @Test
    void noSePuedeSuperponerConOtroTurnoFijo() {
        TurnoFijo otro = TurnoFijo.builder().id(5L).horaInicio(LocalTime.of(19, 0)).horaFin(LocalTime.of(20, 30))
                .build();
        when(turnoFijoRepository.findSuperpuestos(-1L, 1L, DayOfWeek.WEDNESDAY, LocalTime.of(20, 0),
                LocalTime.of(21, 30))).thenReturn(List.of(otro));

        assertThatThrownBy(() -> turnos.crear(pedido(MANIANA, "20:00", "21:30"), admin.getEmail()))
                .isInstanceOf(HorarioOcupadoException.class)
                .hasMessage("Ya hay un turno fijo en esa cancha los miércoles de 19:00 a 20:30 (#5).");
    }

    @Test
    void laProximaFechaEsLaPrimeraEnQueElTurnoNoEmpezo() {
        // hoy martes 10:00
        assertThat(TurnoFijoService.proximaFecha(MANIANA, LocalTime.of(20, 0), AHORA)).isEqualTo(MANIANA);
        assertThat(TurnoFijoService.proximaFecha(AHORA.toLocalDate(), LocalTime.of(9, 0), AHORA))
                .isEqualTo(AHORA.toLocalDate().plusWeeks(1));
        assertThat(TurnoFijoService.proximaFecha(MANIANA.minusWeeks(3), LocalTime.of(20, 0), AHORA))
                .isEqualTo(MANIANA);
    }

    // ── Modificar ────────────────────────────────────────────────────────

    @Test
    void alModificarlosBorraLosPartidosFuturosSinPagosYDejaLosQueTienenPagos() {
        TurnoFijo tf = turnoFijo();
        Reserva sinPagos = partidoDe(tf, 70L, MANIANA);
        Reserva conPagos = partidoDe(tf, 71L, MANIANA.plusWeeks(1));
        TestData.pago(1L, conPagos, EstadoPago.APROBADO, "3750");
        Reserva liberada = partidoDe(tf, 72L, MANIANA.plusWeeks(2));
        liberada.setEstado(EstadoReserva.CANCELADA);
        when(reservaRepository.findIdsFuturosDelTurnoFijo(8L, AHORA.toLocalDate(), AHORA.toLocalTime()))
                .thenReturn(List.of(70L, 71L, 72L));

        TurnoFijoRequest req = pedido(MANIANA, "19:00", "20:30");
        List<Long> sinCambiar = turnos.actualizar(8L, req, admin.getEmail());

        assertThat(sinCambiar).containsExactly(71L);
        verify(stockService).liberar(sinPagos);
        verify(reservaRepository).delete(sinPagos);
        verify(reservaRepository, never()).delete(conPagos);
        verify(reservaRepository, never()).delete(liberada);
        assertThat(tf.getHoraInicio()).isEqualTo(LocalTime.of(19, 0));
        assertThat(tf.getJugadores()).hasSize(4);
    }

    // ── Bajas ────────────────────────────────────────────────────────────

    @Test
    void laBajaGeneralCancelaLosPartidosQueNoEmpezaron() {
        TurnoFijo tf = turnoFijo();
        Reserva proximo = partidoDe(tf, 70L, MANIANA);
        Reserva liberada = partidoDe(tf, 72L, MANIANA.plusWeeks(1));
        liberada.setEstado(EstadoReserva.CANCELADA);
        when(reservaRepository.findIdsFuturosDelTurnoFijo(8L, AHORA.toLocalDate(), AHORA.toLocalTime()))
                .thenReturn(List.of(70L, 72L));

        TurnoFijoResponse r = turnos.darDeBaja(8L, OpcionReembolso.TOTAL, admin.getEmail());

        assertThat(r.getActivo()).isFalse();
        assertThat(tf.getDadoDeBajaAt()).isEqualTo(AHORA);
        verify(cancelacionService).cancelarBloqueada(proximo, admin, true, OpcionReembolso.TOTAL);
        verify(cancelacionService, times(1)).cancelarBloqueada(any(), any(), eq(true), any());

        assertThatThrownBy(() -> turnos.darDeBaja(8L, null, admin.getEmail()))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("El turno fijo #8 está dado de baja.");
    }

    @Test
    void darDeBajaUnaFechaConPartidoLoCancela() {
        TurnoFijo tf = turnoFijo();
        Reserva partido = partidoDe(tf, 70L, MANIANA);
        when(reservaRepository.findByTurnoFijoAndFechaReserva(tf, MANIANA)).thenReturn(Optional.of(partido));

        turnos.darDeBajaFecha(8L, MANIANA, null, admin.getEmail());

        verify(cancelacionService).cancelarBloqueada(partido, admin, true, null);

        partido.setEstado(EstadoReserva.CANCELADA);
        assertThatThrownBy(() -> turnos.darDeBajaFecha(8L, MANIANA, null, admin.getEmail()))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessage("La fecha " + MANIANA + " ya está dada de baja.");
    }

    @Test
    void darDeBajaUnaFechaSinPartidoTodaviaLaRegistraCancelada() {
        TurnoFijo tf = turnoFijo();
        LocalDate dentroDeDosMeses = MANIANA.plusWeeks(8);

        var r = turnos.darDeBajaFecha(8L, dentroDeDosMeses, null, admin.getEmail());

        ArgumentCaptor<Reserva> guardada = ArgumentCaptor.forClass(Reserva.class);
        verify(reservaRepository).save(guardada.capture());
        assertThat(guardada.getValue().getTurnoFijo()).isSameAs(tf);
        assertThat(guardada.getValue().getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        assertThat(guardada.getValue().getTipo()).isEqualTo(TipoReserva.PARTIDO);
        assertThat(r.getFechaReserva()).isEqualTo(dentroDeDosMeses);
        verify(cancelacionService, never()).cancelarBloqueada(any(), any(), eq(true), any());
    }

    @Test
    void unaFechaQueNoEsDelTurnoFijo() {
        turnoFijo();
        assertThatThrownBy(() -> turnos.darDeBajaFecha(8L, MANIANA.plusDays(1), null, admin.getEmail()))
                .isInstanceOf(ReservaException.class)
                .hasMessageContaining("no se juega");
        assertThatThrownBy(() -> turnos.darDeBajaFecha(9L, MANIANA, null, admin.getEmail()))
                .isInstanceOf(TurnoFijoNoEncontradoException.class);
    }

    // ── Generador ────────────────────────────────────────────────────────

    @Test
    void elGeneradorCreaUnaFechaPorSemanaDentroDeLaAnticipacionEInformaLasQueNoPudo() {
        TurnoFijo tf = turnoFijo();
        PartidoService partidoService = mock(PartidoService.class);
        when(partidoService.crearParaTurnoFijo(eq(8L), any())).thenReturn(Optional.of(1L));
        when(partidoService.crearParaTurnoFijo(8L, MANIANA.plusWeeks(2)))
                .thenThrow(new HorarioOcupadoException());
        GeneradorTurnosFijos generador = new GeneradorTurnosFijos(turnoFijoRepository, partidoService,
                TestData.reloj(AHORA), 4);

        GeneradorTurnosFijos.Resultado r = generador.generar(8L);

        // del miércoles 14/10 al 4/11 (hoy + 4 semanas = martes 10/11): 4 fechas, una ocupada
        assertThat(r.creados()).hasSize(3);
        assertThat(r.omitidas()).extracting(TurnoFijoResponse.FechaOmitida::fecha)
                .containsExactly(MANIANA.plusWeeks(2));
        verify(partidoService, times(4)).crearParaTurnoFijo(anyLong(), any());

        tf.setActivo(false);
        assertThat(generador.generar(8L).creados()).isEmpty();
    }
}
