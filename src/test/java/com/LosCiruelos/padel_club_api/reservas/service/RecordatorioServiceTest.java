package com.LosCiruelos.padel_club_api.reservas.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.pago;
import static com.LosCiruelos.padel_club_api.TestData.reserva;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.mapper.ConfiguracionMapper;
import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.notificaciones.aviso.AvisoReserva;
import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;

class RecordatorioServiceTest {

    private ReservaRepository reservaRepository;
    private ReservaService reservaService;
    private ApplicationEventPublisher eventos;
    private Configuracion config;
    private Reserva reserva;

    @BeforeEach
    void setUp() {
        reservaRepository = mock(ReservaRepository.class);
        reservaService = mock(ReservaService.class);
        eventos = mock(ApplicationEventPublisher.class);
        config = TestData.configuracion();

        // turno a las 11:30 (AHORA = 10:00): dentro de las 2 h; señada hace un día
        Usuario cliente = usuario(1L, Role.CLIENTE);
        TestData.perfilDeCliente(cliente); // recibe recordatorios (por defecto)
        reserva = reserva(10L, cliente, EstadoReserva.SEÑADA, "15000");
        reserva.setFechaReserva(AHORA.toLocalDate());
        reserva.setHoraInicio(LocalTime.of(11, 30));
        reserva.setHoraFin(LocalTime.of(13, 0));
        reserva.setCreatedAt(AHORA.minusDays(1));
        Pago senia = pago(1L, reserva, EstadoPago.APROBADO, "7500");
        senia.setPaidAt(AHORA.minusDays(1));
        when(reservaService.bloquear(10L)).thenReturn(reserva);
    }

    private RecordatorioService servicio(LocalDateTime ahora) {
        ConfiguracionRepository repo = mock(ConfiguracionRepository.class);
        when(repo.obtener()).thenReturn(config);
        return new RecordatorioService(reservaRepository, reservaService, new ConfiguracionService(repo, new ConfiguracionMapper()), eventos,
                TestData.reloj(ahora));
    }

    @Test
    void mandaElRecordatorioConElSaldoUnaSolaVez() {
        RecordatorioService recordatorios = servicio(AHORA);

        assertThat(recordatorios.enviar(10L)).isTrue();

        ArgumentCaptor<AvisoReserva> aviso = ArgumentCaptor.forClass(AvisoReserva.class);
        verify(eventos).publishEvent(aviso.capture());
        assertThat(aviso.getValue().tipo()).isEqualTo(TipoAviso.RECORDATORIO);
        assertThat(aviso.getValue().texto()).contains("de 11:30 a 13:00").contains("Saldo a pagar: $7.500.");
        assertThat(reserva.getRecordatorioEnviadoAt()).isEqualTo(AHORA);

        // ya enviado: no se repite
        assertThat(recordatorios.enviar(10L)).isFalse();
    }

    @Test
    void unaReservaPagaDiceQueEstaPaga() {
        pago(2L, reserva, EstadoPago.APROBADO, "7500").setPaidAt(AHORA.minusDays(1));
        reserva.setEstado(EstadoReserva.PAGADA);

        servicio(AHORA).enviar(10L);

        ArgumentCaptor<AvisoReserva> aviso = ArgumentCaptor.forClass(AvisoReserva.class);
        verify(eventos).publishEvent(aviso.capture());
        assertThat(aviso.getValue().texto()).contains("Tu reserva está paga");
    }

    @Test
    void fueraDeLaVentanaNoSeManda() {
        // a las 9:00 faltan 2 h 30 min
        assertThat(servicio(AHORA.minusHours(1)).enviar(10L)).isFalse();
        // el turno ya empezó
        assertThat(servicio(AHORA.plusHours(2)).enviar(10L)).isFalse();
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void siSeReservoDentroDeLaVentanaNoSeManda() {
        reserva.getPagos().getFirst().setPaidAt(AHORA.minusMinutes(5));

        assertThat(servicio(AHORA).enviar(10L)).isFalse();
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void siElClienteNoQuiereRecordatoriosNoSeManda() {
        reserva.getCliente().getClienteProfile().setRecibeRecordatorios(false);

        assertThat(servicio(AHORA).enviar(10L)).isFalse();
        verify(eventos, never()).publishEvent(any());
    }

    @Test
    void enUnPartidoSoloLoRecibenLosJugadoresClientes() {
        Usuario cliente = usuario(2L, Role.CLIENTE);
        TestData.perfilDeCliente(cliente);
        Usuario empleado = usuario(3L, Role.EMPLEADO);
        empleado.asignarRol(Role.EMPLEADO);
        Reserva partido = TestData.partido(20L, usuario(9L, Role.ADMIN), cliente, "15000");
        partido.getJugadores().get(1).setUsuario(empleado); // un EMPLEADO juega en vez de un invitado
        partido.setFechaReserva(AHORA.toLocalDate());
        partido.setHoraInicio(LocalTime.of(11, 30));
        partido.setHoraFin(LocalTime.of(13, 0));
        partido.setCreatedAt(AHORA.minusDays(1));
        when(reservaService.bloquear(20L)).thenReturn(partido);

        assertThat(servicio(AHORA).enviar(20L)).isTrue();

        ArgumentCaptor<AvisoReserva> aviso = ArgumentCaptor.forClass(AvisoReserva.class);
        verify(eventos).publishEvent(aviso.capture()); // uno solo: los invitados no tienen cuenta
        assertThat(aviso.getValue().cliente().email()).isEqualTo(cliente.getEmail());
    }

    @Test
    void unaReservaPendienteOCanceladaNoRecibeRecordatorio() {
        reserva.setEstado(EstadoReserva.CANCELADA);

        assertThat(servicio(AHORA).enviar(10L)).isFalse();
    }

    @Test
    void buscaLosTurnosDeLaVentana() {
        when(reservaRepository.findIdsParaRecordatorio(any(), eq(AHORA.toLocalDate()), eq(LocalTime.of(10, 0)),
                eq(LocalTime.of(12, 0)))).thenReturn(List.of(10L));

        assertThat(servicio(AHORA).pendientes()).containsExactly(10L);
    }

    @Test
    void cerraDeMedianocheTambienBuscaLosDeManiana() {
        LocalDateTime noche = AHORA.withHour(23);
        when(reservaRepository.findIdsParaRecordatorio(any(), eq(noche.toLocalDate()), eq(LocalTime.of(23, 0)),
                eq(LocalTime.MAX))).thenReturn(List.of(1L));
        when(reservaRepository.findIdsParaRecordatorio(any(), eq(noche.toLocalDate().plusDays(1)),
                eq(LocalTime.MIDNIGHT), eq(LocalTime.of(1, 0)))).thenReturn(List.of(2L));

        assertThat(servicio(noche).pendientes()).containsExactly(1L, 2L);
    }

    @Test
    void conElRecordatorioApagadoNoBuscaNada() {
        config.setRecordatorioActivo(false);

        assertThat(servicio(AHORA).pendientes()).isEmpty();
        verify(reservaRepository, never()).findIdsParaRecordatorio(any(), any(), any(), any());
    }
}
