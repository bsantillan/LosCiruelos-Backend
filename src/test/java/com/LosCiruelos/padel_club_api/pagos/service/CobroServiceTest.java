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
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import org.junit.jupiter.api.BeforeEach;

/** Cobros en el club y bonificaciones del ADMIN. */
class CobroServiceTest extends PagosTestBase {

    @Nested
    class PagoManual {

        @Test
        void pagarLaSeniaDejaLaReservaSenada() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            PagoResponse r = cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL, empleado.getEmail());

            assertThat(r.getEstado()).isEqualTo(EstadoPago.APROBADO);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
        }

        @Test
        void pagarElTotalEnUnSoloPagoDejaLaReservaPagada() {
            // antes quedaba PARCIALMENTE_PAGADA porque la lista de pagos en memoria no incluía el nuevo
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            cobroService.registrarPagoManual(10L, new BigDecimal("15000"), PaymentProvider.MANUAL, empleado.getEmail());

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
            assertThat(reserva.getMontoPendiente()).isEqualByComparingTo("0");
        }

        @Test
        void menosQueLaSeniaQuedaParcialYLuegoSePuedeCompletar() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            cobroService.registrarPagoManual(10L, new BigDecimal("3000"), PaymentProvider.MANUAL, empleado.getEmail());
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PARCIALMENTE_PAGADA);

            // antes una reserva PARCIALMENTE_PAGADA no admitía más pagos
            cobroService.registrarPagoManual(10L, new BigDecimal("12000"), PaymentProvider.MANUAL, empleado.getEmail());
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
        }

        @Test
        void montoMayorAlPendienteSeRechaza() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("15000.01"),
                    PaymentProvider.MANUAL, empleado.getEmail()))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("supera el pendiente");
        }

        @Test
        void montoCeroONegativoSeRechaza() {
            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("-5000"),
                    PaymentProvider.MANUAL, empleado.getEmail()))
                    .isInstanceOf(ReservaException.class);
            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, null,
                    PaymentProvider.MANUAL, empleado.getEmail()))
                    .isInstanceOf(ReservaException.class);
        }

        @Test
        void noSeAceptaSiHayUnPagoOnlineEnProceso() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            pago(1L, reserva, EstadoPago.EN_PROCESO, "7500");

            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("7500"),
                    PaymentProvider.MANUAL, empleado.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class);
        }

        @Test
        void unPagoOnlineIniciadoSeDescarta() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago iniciado = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");

            cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL, empleado.getEmail());

            assertThat(iniciado.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
        }

        @Test
        void reservaVencidaNoAdmitePagos() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            reserva.setExpiresAt(AHORA.minusMinutes(1));

            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("7500"),
                    PaymentProvider.MANUAL, empleado.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("expiró");
        }

        @Test
        void cobrarElSaldoDespuesDelTurnoCompletaLaReserva() {
            // jugaron ayer con la seña paga; hoy el empleado cobra el resto en efectivo
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            reserva.setFechaReserva(AHORA.toLocalDate().minusDays(1));
            pago(1L, reserva, EstadoPago.APROBADO, "7500");

            cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL, empleado.getEmail());

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

            cobroService.registrarPagoManual(10L, new BigDecimal("15000"), PaymentProvider.MANUAL, empleado.getEmail());

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
        }

        @Test
        void unaReservaCompletadaYaNoAdmitePagos() {
            reservaBloqueada(EstadoReserva.COMPLETADA);

            assertThatThrownBy(() -> cobroService.registrarPagoManual(10L, new BigDecimal("1"),
                    PaymentProvider.MANUAL, empleado.getEmail()))
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
        void unPartidoSeCobraComoUnTodoSinIndicarElJugador() {
            PagoResponse r = cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL,
                    empleado.getEmail());

            assertThat(r.getMonto()).isEqualByComparingTo("7500");
            assertThat(partido.getMontoPendiente()).isEqualByComparingTo("7500");
            // un partido no tiene seña: con la mitad paga queda parcial
            assertThat(partido.getEstado()).isEqualTo(EstadoReserva.PARCIALMENTE_PAGADA);

            cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MERCADO_PAGO,
                    empleado.getEmail());
            assertThat(partido.getEstado()).isEqualTo(EstadoReserva.PAGADA);
            assertThat(partido.getMontoPendiente()).isEqualByComparingTo("0");
            // los pagos de un partido no le mandan avisos al ADMIN (lo arma y lo sigue él)
            verify(eventos, never()).publishEvent(any());
        }

        @Test
        void bonificarSinMontoCubreTodoLoQueFalta() {
            cobroService.registrarPagoManual(10L, new BigDecimal("1000"), PaymentProvider.MANUAL,
                    empleado.getEmail());

            PagoResponse r = cobroService.bonificar(10L, null, "socio", admin.getEmail());

            assertThat(r.getMonto()).isEqualByComparingTo("14000");
            assertThat(partido.getEstado()).isEqualTo(EstadoReserva.PAGADA);
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
            partido.setEstado(EstadoReserva.PARCIALMENTE_PAGADA);
            pago.setEstado(EstadoPago.DEVUELTO);
            pago.setMontoReembolsado(new BigDecimal("7500"));

            assertThat(partido.recalcularEstado(new BigDecimal("7500"), AHORA)).isTrue();

            assertThat(partido.getEstado()).isEqualTo(EstadoReserva.CONFIRMADA);
        }

        @Test
        void unPartidoNoTieneSeniaYUnaReservaNormalSi() {
            BigDecimal senia = new BigDecimal("7500");
            pago(1L, partido, EstadoPago.APROBADO, "10000");
            partido.recalcularEstado(senia, AHORA);
            assertThat(partido.getEstado()).isEqualTo(EstadoReserva.PARCIALMENTE_PAGADA);

            Reserva normal = TestData.reserva(11L, cliente, EstadoReserva.PENDIENTE, "15000");
            pago(2L, normal, EstadoPago.APROBADO, "10000");
            normal.recalcularEstado(senia, AHORA);
            assertThat(normal.getEstado()).isEqualTo(EstadoReserva.SEÑADA);

            // jugado y sin pagar entero: queda como deuda (no pasa a COMPLETADA)
            partido.setFechaReserva(AHORA.toLocalDate().minusDays(1));
            partido.recalcularEstado(senia, AHORA);
            assertThat(partido.getEstado()).isEqualTo(EstadoReserva.PARCIALMENTE_PAGADA);
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class CuentaDni {

        private Usuario admin;

        @BeforeEach
        void unAdmin() {
            admin = TestData.usuario(3L, Role.ADMIN);
            when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
        }

        private PagoResponse cobrar(String monto) {
            return cobroService.registrarPagoManual(10L, new BigDecimal(monto), PaymentProvider.CUENTA_DNI,
                    empleado.getEmail());
        }

        @Test
        void quedaAprobadoYSinVerificar() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            PagoResponse r = cobrar("7500");

            assertThat(r.getEstado()).isEqualTo(EstadoPago.APROBADO);
            assertThat(r.getProvider()).isEqualTo(PaymentProvider.CUENTA_DNI);
            assertThat(r.getVerificado()).isFalse();
            assertThat(r.getVerificadoAt()).isNull();
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
        }

        @Test
        void elAdminLoVerificaYLoPuedeDesmarcar() {
            reservaBloqueada(EstadoReserva.PENDIENTE);
            Long pagoId = cobrar("7500").getId();

            PagoResponse verificado = cobroService.verificarPago(10L, pagoId, true, admin.getEmail());
            assertThat(verificado.getVerificado()).isTrue();
            assertThat(verificado.getVerificadoAt()).isEqualTo(AHORA);

            PagoResponse desmarcado = cobroService.verificarPago(10L, pagoId, false, admin.getEmail());
            assertThat(desmarcado.getVerificado()).isFalse();
            assertThat(desmarcado.getVerificadoAt()).isNull();
        }

        @Test
        void elEmpleadoNoPuedeVerificar() {
            reservaBloqueada(EstadoReserva.PENDIENTE);
            Long pagoId = cobrar("7500").getId();

            assertThatThrownBy(() -> cobroService.verificarPago(10L, pagoId, true, empleado.getEmail()))
                    .isInstanceOf(PermisoDenegadoException.class)
                    .hasMessage("Solo el ADMIN puede verificar un pago.");
        }

        @Test
        void soloSeVerificanLosPagosConCuentaDni() {
            reservaBloqueada(EstadoReserva.PENDIENTE);
            Long pagoId = cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL,
                    empleado.getEmail()).getId();

            assertThatThrownBy(() -> cobroService.verificarPago(10L, pagoId, true, admin.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("Solo se verifican los pagos con Cuenta DNI o por transferencia.");
            assertThatThrownBy(() -> cobroService.verificarPago(10L, 999L, true, admin.getEmail()))
                    .isInstanceOf(com.LosCiruelos.padel_club_api.pagos.exception.PagoNoEncontradoException.class);
        }

        @Test
        void unPagoEnEfectivoNoTraeVerificado() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            PagoResponse r = cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL,
                    empleado.getEmail());

            assertThat(r.getVerificado()).isNull();
        }

        @Test
        void alCancelarSeDevuelveAMano() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            cobrar("7500");

            reembolsoService.reembolsar(reserva, new BigDecimal("7500"));

            Pago pago = reserva.getPagos().get(0);
            assertThat(pago.getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
            assertThat(pago.getMontoReembolsado()).isEqualByComparingTo("7500");
            verify(mercadoPago, never()).reembolsar(any(), any());
        }
    }

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

            PagoResponse r = cobroService.bonificar(10L, null, "  socio ", admin.getEmail());

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

            cobroService.bonificar(10L, null, null, admin.getEmail());

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
            assertThat(reserva.getMontoPagado()).isEqualByComparingTo("7500");
            assertThat(reserva.getMontoBonificado()).isEqualByComparingTo("7500");
        }

        @Test
        void unaBonificacionParcialCuentaParaLaSenia() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            cobroService.bonificar(10L, new BigDecimal("7500"), null, admin.getEmail());

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
            assertThat(reserva.getMontoPendiente()).isEqualByComparingTo("7500");
        }

        @Test
        void noSeBonificaMasDeLoQueFaltaNiUnaReservaPaga() {
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            pago(1L, reserva, EstadoPago.APROBADO, "7500");

            assertThatThrownBy(() -> cobroService.bonificar(10L, new BigDecimal("8000"), null, admin.getEmail()))
                    .isInstanceOf(ReservaException.class).hasMessageContaining("supera el pendiente");

            pago(2L, reserva, EstadoPago.APROBADO, "7500");
            assertThatThrownBy(() -> cobroService.bonificar(10L, null, null, admin.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class).hasMessage("La reserva ya está paga.");
        }

        @Test
        void soloElAdminPuedeBonificar() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            assertThatThrownBy(() -> cobroService.bonificar(10L, null, null, empleado.getEmail()))
                    .isInstanceOf(com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException.class)
                    .hasMessage("Solo el ADMIN puede bonificar una reserva.");
            verify(pagoRepository, never()).save(any());
        }

        @Test
        void laBonificacionNoAvisaAlAdmin() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            cobroService.bonificar(10L, null, null, admin.getEmail());

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
            com.LosCiruelos.padel_club_api.reservas.dto.request.CrearReservaAdminRequest req =
                    new com.LosCiruelos.padel_club_api.reservas.dto.request.CrearReservaAdminRequest();
            com.LosCiruelos.padel_club_api.pagos.dto.request.BonificacionRequest bonif =
                    new com.LosCiruelos.padel_club_api.pagos.dto.request.BonificacionRequest();
            bonif.setMotivo("torneo");
            req.setBonificacion(bonif);
            when(reservaService.crearParaCliente(req, admin.getEmail())).thenReturn(creada);
            when(reservaConsultaService.obtener(10L, admin.getEmail())).thenReturn(creada);

            assertThat(cobroService.crearReservaBonificada(req, admin.getEmail())).isSameAs(creada);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);

            // un empleado no llega a crearla
            assertThatThrownBy(() -> cobroService.crearReservaBonificada(req, empleado.getEmail()))
                    .isInstanceOf(com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException.class);
            verify(reservaService, org.mockito.Mockito.times(1)).crearParaCliente(any(), anyString());
        }
    }
}
