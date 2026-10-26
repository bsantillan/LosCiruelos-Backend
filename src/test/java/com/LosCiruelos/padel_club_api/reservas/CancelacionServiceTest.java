package com.LosCiruelos.padel_club_api.reservas;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.pago;
import static com.LosCiruelos.padel_club_api.TestData.reserva;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.Services.ConfiguracionService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;
import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.pagos.Pago;
import com.LosCiruelos.padel_club_api.pagos.PagoService;
import com.LosCiruelos.padel_club_api.Services.Avisos.AvisoReserva;
import com.LosCiruelos.padel_club_api.Services.Avisos.TipoAviso;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.pagos.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.ConfiguracionRepository;

class CancelacionServiceTest {

    private ReservaService reservaService;
    private PagoService pagoService;
    private PoliticaCancelacion politica;
    private CancelacionService cancelacion;
    private ApplicationEventPublisher eventos;
    private UsuarioService usuarioService;

    private Usuario cliente;
    private Usuario empleado;
    private Reserva reserva;

    @BeforeEach
    void setUp() {
        ConfiguracionRepository configuracionRepository = mock(ConfiguracionRepository.class);
        when(configuracionRepository.obtener()).thenReturn(TestData.configuracion()); // 24 h total, 12 h seña
        politica = new PoliticaCancelacion(new ConfiguracionService(configuracionRepository));

        reservaService = mock(ReservaService.class);
        pagoService = mock(PagoService.class);
        usuarioService = mock(UsuarioService.class);
        eventos = mock(ApplicationEventPublisher.class);
        cancelacion = new CancelacionService(reservaService, new ReservaMapper(new ConfiguracionService(configuracionRepository), politica,
                TestData.reloj(AHORA)), pagoService, politica, usuarioService,
                TestData.reloj(AHORA), eventos);

        cliente = usuario(1L, Role.CLIENTE);
        empleado = usuario(2L, Role.EMPLEADO);
        for (Usuario u : List.of(cliente, empleado)) {
            when(usuarioService.autenticado(u.getEmail())).thenReturn(u);
        }
        reserva = reserva(10L, cliente, EstadoReserva.PAGADA, "15000");
        when(reservaService.bloquearConAcceso(eq(10L), any())).thenReturn(reserva);
    }

    /** Ubica el inicio del turno a {@code horas} de AHORA. */
    private void turnoEn(long horas) {
        LocalDateTime inicio = AHORA.plusHours(horas);
        reserva.setFechaReserva(inicio.toLocalDate());
        reserva.setHoraInicio(inicio.toLocalTime());
        reserva.setHoraFin(inicio.toLocalTime().plusMinutes(90));
    }

    private void pagada(String monto) {
        pago(1L, reserva, EstadoPago.APROBADO, monto);
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Cliente {

        @Test
        void con24HorasOMasSeLeDevuelveTodo() {
            turnoEn(30);
            pagada("15000");

            cancelacion.cancelar(10L, cliente.getEmail(), null);

            verify(pagoService).reembolsar(reserva, new BigDecimal("15000"));
            verify(reservaService).cancelar(reserva);
        }

        @Test
        void entre12y24HorasSeLeDevuelveLaSenia() {
            turnoEn(20);
            pagada("15000");

            cancelacion.cancelar(10L, cliente.getEmail(), null);

            verify(pagoService).reembolsar(reserva, new BigDecimal("7500.00"));
        }

        @Test
        void siSoloPagoParteDeLaSeniaSeDevuelveLoPagado() {
            turnoEn(20);
            reserva.setEstado(EstadoReserva.PARCIALMENTE_PAGADA);
            pagada("3000");

            cancelacion.cancelar(10L, cliente.getEmail(), null);

            verify(pagoService).reembolsar(reserva, new BigDecimal("3000"));
        }

        @Test
        void conMenosDe12HorasCancelaSinReembolso() {
            turnoEn(5);
            pagada("15000");

            cancelacion.cancelar(10L, cliente.getEmail(), null);

            verify(pagoService, never()).reembolsar(any(), any());
            verify(reservaService).cancelar(reserva);
        }

        @Test
        void siElTurnoEmpezoNoPuedeCancelar() {
            turnoEn(-1);
            pagada("15000");

            assertThatThrownBy(() -> cancelacion.cancelar(10L, cliente.getEmail(), null))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("empezó");
            verify(reservaService, never()).cancelar(any());
        }

        @Test
        void noPuedeElegirElReembolso() {
            assertThatThrownBy(() -> cancelacion.cancelar(10L, cliente.getEmail(), OpcionReembolso.TOTAL))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("personal");
        }

        @Test
        void unaPendienteSeCancelaSinReembolso() {
            turnoEn(30);
            reserva.setEstado(EstadoReserva.PENDIENTE);

            cancelacion.cancelar(10L, cliente.getEmail(), null);

            verify(pagoService, never()).reembolsar(any(), any());
            verify(reservaService).cancelar(reserva);
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Personal {

        @Test
        void eligeQueSeDevuelveSinImportarLaAnticipacion() {
            turnoEn(2);
            pagada("15000");

            cancelacion.cancelar(10L, empleado.getEmail(), OpcionReembolso.TOTAL);

            verify(pagoService).reembolsar(reserva, new BigDecimal("15000"));
        }

        @Test
        void puedeDevolverSoloLaSeniaONada() {
            turnoEn(40);
            pagada("15000");

            cancelacion.cancelar(10L, empleado.getEmail(), OpcionReembolso.NADA);

            verify(pagoService, never()).reembolsar(any(), any());
            verify(reservaService).cancelar(reserva);
        }

        @Test
        void conDineroCobradoTieneQueIndicarElReembolso() {
            turnoEn(40);
            pagada("15000");

            assertThatThrownBy(() -> cancelacion.cancelar(10L, empleado.getEmail(), null))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("TOTAL, SENIA o NADA");
        }

        @Test
        void puedeCancelarConElTurnoEmpezadoPeroNoTerminado() {
            turnoEn(-1); // empezó hace 1 h, dura 90 min
            pagada("15000");

            cancelacion.cancelar(10L, empleado.getEmail(), OpcionReembolso.SENIA);

            verify(pagoService).reembolsar(reserva, new BigDecimal("7500.00"));
        }

        @Test
        void unTurnoTerminadoNoSeCancela() {
            turnoEn(-3);
            pagada("15000");

            assertThatThrownBy(() -> cancelacion.cancelar(10L, empleado.getEmail(), OpcionReembolso.TOTAL))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("terminó");
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Avisos {

        private List<AvisoReserva> publicados() {
            ArgumentCaptor<AvisoReserva> captor = ArgumentCaptor.forClass(AvisoReserva.class);
            verify(eventos, org.mockito.Mockito.atLeastOnce()).publishEvent(captor.capture());
            return captor.getAllValues();
        }

        @Test
        void siCancelaElClienteSoloSeAvisaAlAdmin() {
            turnoEn(30);
            pagada("15000");

            cancelacion.cancelar(10L, cliente.getEmail(), null);

            List<AvisoReserva> avisos = publicados();
            assertThat(avisos).extracting(AvisoReserva::tipo).containsExactly(TipoAviso.RESERVA_CANCELADA);
            assertThat(avisos.getFirst().detalle()).contains("Canceló el cliente").contains("$15.000");
        }

        @Test
        void siCancelaElClubTambienSeAvisaAlCliente() {
            turnoEn(30);
            pagada("15000");

            cancelacion.cancelar(10L, empleado.getEmail(), OpcionReembolso.TOTAL);

            List<AvisoReserva> avisos = publicados();
            assertThat(avisos).extracting(AvisoReserva::tipo)
                    .containsExactly(TipoAviso.RESERVA_CANCELADA, TipoAviso.CANCELADA_POR_CLUB);
            assertThat(avisos.get(0).detalle()).contains("Canceló el club");
            assertThat(avisos.get(1).cliente().email()).isEqualTo(cliente.getEmail());
            assertThat(avisos.get(1).detalle()).contains("Te devolvemos $15.000");
        }

        @Test
        void elAvisoDiceLoQueHayQueDevolverAMano() {
            turnoEn(30);
            Pago enElClub = pago(1L, reserva, EstadoPago.APROBADO, "15000");
            // lo que deja PagoService.reembolsar con un pago cobrado en el club
            org.mockito.Mockito.doAnswer(inv -> {
                enElClub.setMontoReembolsado(new BigDecimal("15000"));
                enElClub.setEstado(EstadoPago.A_REEMBOLSAR);
                return null;
            }).when(pagoService).reembolsar(any(), any());

            cancelacion.cancelar(10L, cliente.getEmail(), null);

            assertThat(publicados().getFirst().detalle()).contains("Hay que devolver $15.000 a mano");
        }
    }

    @Test
    void elAvisoDiceSiElReembolsoSalioBien() {
        Pago devuelto = pago(1L, reserva, EstadoPago.DEVUELTO, "5000");
        devuelto.setExternalPaymentId("mp-1");
        devuelto.setMontoReembolsado(new BigDecimal("5000"));
        Pago pendiente = pago(2L, reserva, EstadoPago.REEMBOLSO_PENDIENTE, "4000");
        pendiente.setExternalPaymentId("mp-2");
        Pago rechazado = pago(3L, reserva, EstadoPago.A_REEMBOLSAR, "3000");
        rechazado.setExternalPaymentId("mp-3");
        pago(4L, reserva, EstadoPago.A_REEMBOLSAR, "2000"); // cobrado en el club

        String texto = CancelacionService.resultadoDelReembolso(reserva, new BigDecimal("14000"));

        assertThat(texto).startsWith("Reembolso de $14.000:")
                .contains("Mercado Pago devolvió $5.000 (exitoso)")
                .contains("todavía no aceptó $4.000")
                .contains("rechazó $3.000: hay que devolverlo a mano (fallido)")
                .contains("Hay que devolver $2.000 a mano (se cobró en el club)");
        assertThat(CancelacionService.resultadoDelReembolso(reserva, BigDecimal.ZERO)).isEqualTo("Sin reembolso.");
    }

    @Test
    void reservasFinalizadasOConPagoEnProcesoNoSeCancelan() {
        turnoEn(30);
        for (EstadoReserva estado : List.of(EstadoReserva.COMPLETADA, EstadoReserva.CANCELADA, EstadoReserva.EXPIRADA)) {
            reserva.setEstado(estado);
            assertThatThrownBy(() -> cancelacion.cancelar(10L, cliente.getEmail(), null))
                    .isInstanceOf(EstadoInvalidoException.class);
        }

        reserva.setEstado(EstadoReserva.PENDIENTE);
        pago(2L, reserva, EstadoPago.EN_PROCESO, "7500");
        assertThatThrownBy(() -> cancelacion.cancelar(10L, cliente.getEmail(), null))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("en proceso");
    }

    @Test
    void laPoliticaInformaHastaCuandoYCuantoSeDevuelve() {
        turnoEn(30);
        pagada("15000");

        PoliticaCancelacion.Evaluacion e = politica.evaluarParaCliente(reserva, AHORA);

        assertThat(e.cancelable()).isTrue();
        assertThat(e.cancelableHasta()).isEqualTo(AHORA.plusHours(30));
        assertThat(e.montoReembolso()).isEqualByComparingTo("15000");
        assertThat(e.reembolsoTotalHasta()).isEqualTo(AHORA.plusHours(6));
        assertThat(e.reembolsoSeniaHasta()).isEqualTo(AHORA.plusHours(18));

        reserva.setEstado(EstadoReserva.COMPLETADA);
        assertThat(politica.evaluarParaCliente(reserva, AHORA).cancelable()).isFalse();
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Partidos {

        private Usuario admin;
        private Reserva partido;

        @BeforeEach
        void partido() {
            admin = usuario(3L, Role.ADMIN);
            when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
            partido = TestData.partido(20L, admin, cliente, "15000");
            partido.setFechaReserva(AHORA.toLocalDate().plusDays(2));
            when(reservaService.bloquearConAcceso(eq(20L), any())).thenReturn(partido);
        }

        @Test
        void soloElAdminCancelaUnPartido() {
            assertThatThrownBy(() -> cancelacion.cancelar(20L, empleado.getEmail(), OpcionReembolso.NADA))
                    .isInstanceOf(com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException.class)
                    .hasMessage("Solo el ADMIN puede cancelar un partido.");
            verify(reservaService, never()).cancelar(any());
        }

        @Test
        void sinPagosSeCancelaYSeAvisaACadaJugadorRegistrado() {
            cancelacion.cancelar(20L, admin.getEmail(), null);

            verify(reservaService).cancelar(partido);
            ArgumentCaptor<AvisoReserva> aviso = ArgumentCaptor.forClass(AvisoReserva.class);
            verify(eventos).publishEvent(aviso.capture());
            // solo el jugador 1 tiene cuenta; no se avisa a los ADMIN (lo canceló uno de ellos)
            assertThat(aviso.getValue().tipo()).isEqualTo(TipoAviso.CANCELADA_POR_CLUB);
            assertThat(aviso.getValue().cliente().email()).isEqualTo(cliente.getEmail());
        }

        @Test
        void conPlataCobradaHayQueIndicarElReembolso() {
            pago(1L, partido, EstadoPago.APROBADO, "7500");
            partido.setEstado(EstadoReserva.SEÑADA);

            assertThatThrownBy(() -> cancelacion.cancelar(20L, admin.getEmail(), null))
                    .isInstanceOf(ReservaException.class);
            cancelacion.cancelar(20L, admin.getEmail(), OpcionReembolso.TOTAL);
            verify(pagoService).reembolsar(eq(partido), eq(new java.math.BigDecimal("7500")));
        }

        @Test
        void unJugadorNoPuedeCancelarloNiVeQueSeaCancelable() {
            assertThat(politica.evaluarParaCliente(partido, AHORA).cancelable()).isFalse();
        }
    }
}
