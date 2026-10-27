package com.LosCiruelos.padel_club_api.pagos.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.pago;
import static com.LosCiruelos.padel_club_api.TestData.reserva;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;

/** Cobros en el club y bonificaciones del ADMIN. */
class CobroServiceTest extends PagosTestBase {

    @Nested
    class PagoManual {

        @Test
        void pagarLaSeniaDejaLaReservaSenada() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            PagoResponse r = cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL,
                    null, empleado.getEmail());

            assertThat(r.getEstado()).isEqualTo(EstadoPago.APROBADO);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
        }

        @Test
        void pagarElTotalEnUnSoloPagoDejaLaReservaPagada() {
            // antes quedaba PARCIALMENTE_PAGADA porque la lista de pagos en memoria no incluía el nuevo
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            cobroService.registrarPagoManual(10L, new BigDecimal("15000"), PaymentProvider.MANUAL, null, empleado.getEmail());

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
            assertThat(reserva.getMontoPendiente()).isEqualByComparingTo("0");
        }

        @Test
        void menosQueLaSeniaQuedaParcialYLuegoSePuedeCompletar() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            cobroService.registrarPagoManual(10L, new BigDecimal("3000"), PaymentProvider.MANUAL, null, empleado.getEmail());
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PARCIALMENTE_PAGADA);

            // antes una reserva PARCIALMENTE_PAGADA no admitía más pagos
            cobroService.registrarPagoManual(10L, new BigDecimal("12000"), PaymentProvider.MANUAL, null, empleado.getEmail());
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
        }

        @Test
        void montoMayorAlPendienteSeRechaza() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("15000.01"),
                    PaymentProvider.MANUAL, null, empleado.getEmail()))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("supera el pendiente");
        }

        @Test
        void montoCeroONegativoSeRechaza() {
            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("-5000"),
                    PaymentProvider.MANUAL, null, empleado.getEmail()))
                    .isInstanceOf(ReservaException.class);
            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, null,
                    PaymentProvider.MANUAL, null, empleado.getEmail()))
                    .isInstanceOf(ReservaException.class);
        }

        @Test
        void noSeAceptaSiHayUnPagoOnlineEnProceso() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            pago(1L, reserva, EstadoPago.EN_PROCESO, "7500");

            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("7500"),
                    PaymentProvider.MANUAL, null, empleado.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class);
        }

        @Test
        void unPagoOnlineIniciadoSeDescarta() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago iniciado = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");

            cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL, null, empleado.getEmail());

            assertThat(iniciado.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
        }

        @Test
        void reservaVencidaNoAdmitePagos() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            reserva.setExpiresAt(AHORA.minusMinutes(1));

            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("7500"),
                    PaymentProvider.MANUAL, null, empleado.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("expiró");
        }

        @Test
        void cobrarElSaldoDespuesDelTurnoCompletaLaReserva() {
            // jugaron ayer con la seña paga; hoy el empleado cobra el resto en efectivo
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            reserva.setFechaReserva(AHORA.toLocalDate().minusDays(1));
            pago(1L, reserva, EstadoPago.APROBADO, "7500");

            cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL, null, empleado.getEmail());

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.COMPLETADA);
            assertThat(reserva.getMontoPendiente()).isEqualByComparingTo("0");
        }

        @Test
        void cobrarElTotalAntesODuranteElTurnoLaDejaPagadaNoCompletada() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            // el turno empezó hace 30 minutos y todavía no terminó
            reserva.setFechaReserva(AHORA.toLocalDate());
            reserva.setHoraInicio(AHORA.toLocalTime().minusMinutes(30));
            reserva.setHoraFin(AHORA.toLocalTime().plusMinutes(60));

            cobroService.registrarPagoManual(10L, new BigDecimal("15000"), PaymentProvider.MANUAL, null, empleado.getEmail());

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
        }

        @Test
        void unaReservaCompletadaYaNoAdmitePagos() {
            reservaBloqueada(EstadoReserva.COMPLETADA);

            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("1"),
                    PaymentProvider.MANUAL, null, empleado.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class);
        }
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
            partido = TestData.partido(10L, admin, cliente, "15000");
            when(reservaService.bloquearConAcceso(eq(10L), any())).thenReturn(partido);
        }

        @Test
        void cadaPagoQuedaANombreDelJugadorYElPartidoPasaDeConfirmadaASeniadaYPagada() {
            PagoResponse r = cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL,
                    202L, empleado.getEmail());

            assertThat(r.getJugadorId()).isEqualTo(202L);
            assertThat(partido.getPagos().get(0).getJugador().getId()).isEqualTo(202L);
            assertThat(partido.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
            // el jugador 2 cubrió su parte (3750) y la del 3: la reserva sigue debiendo 7500
            assertThat(partido.pagadoPor(partido.getJugadores().get(1))).isEqualByComparingTo("7500");
            assertThat(partido.debeDe(partido.getJugadores().get(1))).isEqualByComparingTo("0");
            assertThat(partido.debeDe(partido.getJugadores().get(0))).isEqualByComparingTo("3750");

            cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MERCADO_PAGO, 201L,
                    empleado.getEmail());
            assertThat(partido.getEstado()).isEqualTo(EstadoReserva.PAGADA);
            // pagado entre dos: nadie debe nada
            assertThat(partido.getJugadores())
                    .allSatisfy(j -> assertThat(partido.debeDe(j)).isEqualByComparingTo("0"));
            // los pagos de un partido no le mandan avisos al ADMIN (lo arma y lo sigue él)
            verify(eventos, never()).publishEvent(any());
        }

        @Test
        void hayQueIndicarUnJugadorDelPartido() {
            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("3750"),
                    PaymentProvider.MANUAL, null, empleado.getEmail()))
                    .isInstanceOf(ReservaException.class)
                    .hasMessage("Indicá qué jugador paga (jugadorId).");
            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("3750"),
                    PaymentProvider.MANUAL, 999L, empleado.getEmail()))
                    .hasMessage("El jugador 999 no es de este partido.");
            assertThat(partido.getPagos()).isEmpty();
        }

        @Test
        void enUnaReservaNormalNoSeIndicaJugador() {
            reservaBloqueada(EstadoReserva.SEÑADA);
            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("3750"),
                    PaymentProvider.MANUAL, 201L, empleado.getEmail()))
                    .hasMessage("La reserva no es un partido: no se indica el jugador.");
        }

        @Test
        void bonificarSinMontoCubreLoQueLeFaltaAlJugador() {
            cobroService.registrarPagoManual(10L, new BigDecimal("1000"), PaymentProvider.MANUAL, 203L,
                    empleado.getEmail());

            PagoResponse r = cobroService.bonificar(10L, null, "socio", 203L, admin.getEmail());

            assertThat(r.getMonto()).isEqualByComparingTo("2750");
            assertThat(r.getJugadorId()).isEqualTo(203L);
            assertThatThrownBy(() -> cobroService.bonificar(10L, null, null, 203L, admin.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("Invitado 3 ya cubrió su parte.");
        }

        @Test
        void unPartidoNoSePagaOnline() {
            assertThatThrownBy(() -> pagoOnlineService.iniciarPago(10L, false, admin.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("Los partidos se pagan en el club.");
        }

        @Test
        void unPartidoQueSeQuedaSinPagosVuelveAConfirmadaYNoSeCancela() {
            Pago pago = pago(1L, partido, EstadoPago.APROBADO, "7500");
            partido.setEstado(EstadoReserva.SEÑADA);
            pago.setEstado(EstadoPago.DEVUELTO);
            pago.setMontoReembolsado(new BigDecimal("7500"));

            assertThat(partido.recalcularEstado(new BigDecimal("7500"), AHORA)).isTrue();

            assertThat(partido.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Bonificacion {

        private Usuario admin;

        @BeforeEach
        void unAdmin() {
            admin = usuario(3L, Role.ADMIN);
            when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
        }

        @Test
        void sinMontoBonificaTodoYLaReservaQuedaPagaSinPlataCobrada() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            PagoResponse r = cobroService.bonificar(10L, null, "  socio ", null, admin.getEmail());

            assertThat(r.getProvider()).isEqualTo(PaymentProvider.BONIFICACION);
            assertThat(r.getMonto()).isEqualByComparingTo("15000");
            assertThat(r.getMotivo()).isEqualTo("socio");
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
            assertThat(reserva.getMontoPagado()).isEqualByComparingTo("0");
            assertThat(reserva.getMontoBonificado()).isEqualByComparingTo("15000");
            assertThat(reserva.getMontoPendiente()).isEqualByComparingTo("0");
            assertThat(reserva.getPagos().getFirst().getRegistradoPorId()).isEqualTo(3L);
        }

        @Test
        void bonificarElSaldoDeUnaSenadaLaDejaPaga() {
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            pago(1L, reserva, EstadoPago.APROBADO, "7500").setExternalPaymentId("mp-1");

            cobroService.bonificar(10L, null, null, null, admin.getEmail());

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
            assertThat(reserva.getMontoPagado()).isEqualByComparingTo("7500");
            assertThat(reserva.getMontoBonificado()).isEqualByComparingTo("7500");
        }

        @Test
        void unaBonificacionParcialCuentaParaLaSenia() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            cobroService.bonificar(10L, new BigDecimal("7500"), null, null, admin.getEmail());

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
            assertThat(reserva.getMontoPendiente()).isEqualByComparingTo("7500");
        }

        @Test
        void noSeBonificaMasDeLoQueFaltaNiUnaReservaPaga() {
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            pago(1L, reserva, EstadoPago.APROBADO, "7500");

            assertThatThrownBy(() -> cobroService.bonificar(10L, new BigDecimal("8000"), null, null, admin.getEmail()))
                    .isInstanceOf(ReservaException.class).hasMessageContaining("supera el pendiente");

            pago(2L, reserva, EstadoPago.APROBADO, "7500");
            assertThatThrownBy(() -> cobroService.bonificar(10L, null, null, null, admin.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class).hasMessage("La reserva ya está paga.");
        }

        @Test
        void soloElAdminPuedeBonificar() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            assertThatThrownBy(() -> cobroService.bonificar(10L, null, null, null, empleado.getEmail()))
                    .isInstanceOf(com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException.class)
                    .hasMessage("Solo el ADMIN puede bonificar una reserva.");
            verify(pagoRepository, never()).save(any());
        }

        @Test
        void laBonificacionNoAvisaAlAdmin() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            cobroService.bonificar(10L, null, null, null, admin.getEmail());

            verify(eventos, never()).publishEvent(any());
        }

        @Test
        void alCancelarNoSeDevuelveLaBonificacion() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PAGADA);
            Pago bonificacion = pago(1L, reserva, EstadoPago.APROBADO, "7500");
            bonificacion.setProvider(PaymentProvider.BONIFICACION);
            Pago enElClub = pago(2L, reserva, EstadoPago.APROBADO, "7500");
            enElClub.setProvider(PaymentProvider.MANUAL);

            reembolsoService.reembolsar(reserva, reserva.getMontoPagado());

            assertThat(bonificacion.getEstado()).isEqualTo(EstadoPago.APROBADO);
            assertThat(enElClub.getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
            assertThat(enElClub.getMontoReembolsado()).isEqualByComparingTo("7500");
        }

        @Test
        void crearConBonificacionCreaYBonificaEnLaMismaOperacion() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            ReservaResponse creada = new ReservaResponse();
            creada.setId(10L);
            com.LosCiruelos.padel_club_api.reservas.dto.request.CrearReservaRequest req =
                    new com.LosCiruelos.padel_club_api.reservas.dto.request.CrearReservaRequest();
            com.LosCiruelos.padel_club_api.pagos.dto.request.BonificacionRequest bonif =
                    new com.LosCiruelos.padel_club_api.pagos.dto.request.BonificacionRequest();
            bonif.setMotivo("torneo");
            req.setBonificacion(bonif);
            when(reservaService.crearReserva(req, admin.getEmail())).thenReturn(creada);
            when(reservaConsultaService.obtener(10L, admin.getEmail())).thenReturn(creada);

            assertThat(cobroService.crearReservaBonificada(req, admin.getEmail())).isSameAs(creada);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);

            // un empleado no llega a crearla
            assertThatThrownBy(() -> cobroService.crearReservaBonificada(req, empleado.getEmail()))
                    .isInstanceOf(com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException.class);
            verify(reservaService, org.mockito.Mockito.times(1)).crearReserva(any(), anyString());
        }
    }
}
