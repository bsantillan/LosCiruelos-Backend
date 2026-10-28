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
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.EstadoPayment;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentResult;
import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReembolsoFallidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.notificaciones.aviso.AvisoReserva;
import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;

/** Pagos online: iniciar, cobrar con el Brick, webhook y avisos al ADMIN. */
class PagoOnlineServiceTest extends PagosTestBase {

    @Nested
    class IniciarPago {

        @Test
        void seniaCobraElPorcentajeConfigurado() {
            reservaBloqueada(EstadoReserva.PENDIENTE);
            when(mercadoPago.crearPreferencia(any())).thenReturn("pref-1");

            PagoResponse r = pagoOnlineService.iniciarPago(10L, false, cliente.getEmail());

            assertThat(r.getMonto()).isEqualByComparingTo("7500");
            assertThat(r.getPaymentToken()).isEqualTo("pref-1");
            assertThat(r.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
        }

        @Test
        void totalCobraTodoLoPendiente() {
            reservaBloqueada(EstadoReserva.PENDIENTE);
            when(mercadoPago.crearPreferencia(any())).thenReturn("pref-1");

            assertThat(pagoOnlineService.iniciarPago(10L, true, cliente.getEmail()).getMonto()).isEqualByComparingTo("15000");
        }

        @Test
        void reservaSenadaPuedePagarElSaldoOnline() {
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            pago(1L, reserva, EstadoPago.APROBADO, "7500");
            when(mercadoPago.crearPreferencia(any())).thenReturn("pref-2");

            assertThat(pagoOnlineService.iniciarPago(10L, true, cliente.getEmail()).getMonto()).isEqualByComparingTo("7500");
            assertThatThrownBy(() -> pagoOnlineService.iniciarPago(10L, false, cliente.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("seña ya está paga");
        }

        @Test
        void siYaHayUnPagoIniciadoPorElMismoMontoSeReutiliza() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago iniciado = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");
            iniciado.setPaymentToken("pref-existente");

            PagoResponse r = pagoOnlineService.iniciarPago(10L, false, cliente.getEmail());

            assertThat(r.getId()).isEqualTo(1L);
            verify(mercadoPago, never()).crearPreferencia(any());
        }

        @Test
        void unPagoIniciadoPorOtroMontoSeDescarta() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago iniciado = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");
            iniciado.setPaymentToken("pref-seña");
            when(mercadoPago.crearPreferencia(any())).thenReturn("pref-total");

            PagoResponse r = pagoOnlineService.iniciarPago(10L, true, cliente.getEmail());

            assertThat(iniciado.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
            assertThat(r.getMonto()).isEqualByComparingTo("15000");
        }

        @Test
        void noSePuedeIniciarConUnPagoEnProceso() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            pago(1L, reserva, EstadoPago.EN_PROCESO, "7500");

            assertThatThrownBy(() -> pagoOnlineService.iniciarPago(10L, false, cliente.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class);
        }

        @Test
        void reservaCanceladaNoAdmitePagos() {
            reservaBloqueada(EstadoReserva.CANCELADA);

            assertThatThrownBy(() -> pagoOnlineService.iniciarPago(10L, false, cliente.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class CobroConBrick {

        @Test
        void elMontoQueSeCobraEsElDelServidorNoElDelFront() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            pago(1L, reserva, EstadoPago.PENDIENTE, "7500");
            when(mercadoPago.cobrar(any(), anyMap())).thenReturn(resultado(EstadoPayment.APROBADO, "7500", "mp-1", 1L));

            pagoOnlineService.procesarPago(10L, Map.of("transaction_amount", 1, "token", "tok"), cliente.getEmail());

            ArgumentCaptor<Pago> cobrado = ArgumentCaptor.forClass(Pago.class);
            verify(mercadoPago).cobrar(cobrado.capture(), anyMap());
            assertThat(cobrado.getValue().getMonto()).isEqualByComparingTo("7500");
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
        }

        @Test
        void pagoRechazadoDejaLaReservaPendiente() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");
            when(mercadoPago.cobrar(any(), anyMap())).thenReturn(resultado(EstadoPayment.RECHAZADO, "7500", "mp-1", 1L));

            PagoResponse r = pagoOnlineService.procesarPago(10L, Map.of(), cliente.getEmail());

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.RECHAZADO);
            assertThat(pago.getExternalPaymentId()).isEqualTo("mp-1");
            assertThat(r.getEstadoReserva()).isEqualTo(EstadoReserva.PENDIENTE);
        }

        @Test
        void pagoEnProcesoQuedaEnProcesoHastaElWebhook() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");
            when(mercadoPago.cobrar(any(), anyMap())).thenReturn(resultado(EstadoPayment.EN_PROCESO, "7500", "mp-1", 1L));

            pagoOnlineService.procesarPago(10L, Map.of(), cliente.getEmail());

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.EN_PROCESO);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
            assertThat(reserva.tienePagoActivo()).isTrue();
        }

        @Test
        void sinPagoIniciadoNoSeCobra() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            assertThatThrownBy(() -> pagoOnlineService.procesarPago(10L, Map.of(), cliente.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class);
            verify(mercadoPago, never()).cobrar(any(), anyMap());
        }

        @Test
        void reservaCanceladaNoSeCobraAunqueTengaUnPagoIniciado() {
            Reserva reserva = reservaBloqueada(EstadoReserva.CANCELADA);
            pago(1L, reserva, EstadoPago.PENDIENTE, "7500");

            assertThatThrownBy(() -> pagoOnlineService.procesarPago(10L, Map.of(), cliente.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class);
            verify(mercadoPago, never()).cobrar(any(), anyMap());
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Webhook {

        @Test
        void aprobacionDeUnPagoEnProcesoConfirmaLaReserva() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.EN_PROCESO, "7500");
            pago.setExternalPaymentId("mp-1");
            when(mercadoPago.consultarPago("mp-1")).thenReturn(resultado(EstadoPayment.APROBADO, "7500", "mp-1", 1L));
            when(pagoRepository.findReservaIdByExternalPaymentId("mp-1")).thenReturn(Optional.of(10L));

            pagoOnlineService.procesarNotificacion("mp-1");

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
        }

        @Test
        void pagoConWalletSinIdExternoSeEncuentraPorMetadata() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(7L, reserva, EstadoPago.PENDIENTE, "15000");
            when(mercadoPago.consultarPago("mp-9")).thenReturn(resultado(EstadoPayment.APROBADO, "15000", "mp-9", 7L));
            when(pagoRepository.findReservaIdByExternalPaymentId("mp-9")).thenReturn(Optional.empty());
            when(pagoRepository.findReservaIdByPagoId(7L)).thenReturn(Optional.of(10L));

            pagoOnlineService.procesarNotificacion("mp-9");

            assertThat(pago.getExternalPaymentId()).isEqualTo("mp-9");
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
        }

        @Test
        void notificacionSinPagoAsociadoSeIgnora() {
            when(mercadoPago.consultarPago("mp-x")).thenReturn(resultado(EstadoPayment.APROBADO, "1", "mp-x", null));
            when(pagoRepository.findReservaIdByExternalPaymentId("mp-x")).thenReturn(Optional.empty());

            pagoOnlineService.procesarNotificacion("mp-x");

            verify(reservaService, never()).bloquear(anyLong());
        }

        @Test
        void aprobacionSobreReservaCanceladaQuedaParaReembolsar() {
            // antes revivía la reserva cancelada y podía quedar superpuesta con otra
            Reserva reserva = reservaBloqueada(EstadoReserva.CANCELADA);
            Pago pago = pago(1L, reserva, EstadoPago.EN_PROCESO, "7500");

            pagoOnlineService.aplicarResultado(reserva, pago, resultado(EstadoPayment.APROBADO, "7500", "mp-1", 1L));

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
            // la plata entró (cierre de caja) y queda por devolver desde ahora
            assertThat(pago.getPaidAt()).isEqualTo(AHORA);
            assertThat(pago.getReembolsadoAt()).isEqualTo(AHORA);
        }

        @Test
        void aprobacionConMontoDistintoQuedaParaReembolsar() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");

            pagoOnlineService.aplicarResultado(reserva, pago, resultado(EstadoPayment.APROBADO, "1", "mp-1", 1L));

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        }

        @Test
        void rechazoNoDesSeniaUnaReservaConfirmada() {
            // antes forzaba la reserva a PENDIENTE
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            pago(1L, reserva, EstadoPago.APROBADO, "7500");
            Pago segundo = pago(2L, reserva, EstadoPago.EN_PROCESO, "7500");
            segundo.setExternalPaymentId("mp-2");

            pagoOnlineService.aplicarResultado(reserva, segundo, resultado(EstadoPayment.RECHAZADO, "7500", "mp-2", 2L));

            assertThat(segundo.getEstado()).isEqualTo(EstadoPago.RECHAZADO);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
        }

        @Test
        void notificacionRepetidaNoCambiaNada() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");
            PaymentResult aprobado = resultado(EstadoPayment.APROBADO, "7500", "mp-1", 1L);

            pagoOnlineService.aplicarResultado(reserva, pago, aprobado);
            var pagadoEn = pago.getPaidAt();
            pagoOnlineService.aplicarResultado(reserva, pago, aprobado);

            assertThat(pago.getPaidAt()).isEqualTo(pagadoEn);
            assertThat(reserva.getMontoPagado()).isEqualByComparingTo("7500");
        }

        @Test
        void unPagoDevueltoNoVuelveAAprobarse() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.DEVUELTO, "7500");

            pagoOnlineService.aplicarResultado(reserva, pago, resultado(EstadoPayment.APROBADO, "7500", "mp-1", 1L));

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.DEVUELTO);
        }

        @Test
        void reembolsoTotalCancelaLaReserva() {
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            Pago pago = pago(1L, reserva, EstadoPago.APROBADO, "7500");
            pago.setExternalPaymentId("mp-1");

            pagoOnlineService.aplicarResultado(reserva, pago, resultado(EstadoPayment.DEVUELTO, "7500", "mp-1", 1L));

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.DEVUELTO);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
            assertThat(pago.getReembolsadoAt()).isEqualTo(AHORA);
        }

        @Test
        void rechazoDeOtroIntentoNoPisaUnPagoEnCurso() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.EN_PROCESO, "7500");
            pago.setExternalPaymentId("mp-nuevo");

            pagoOnlineService.aplicarResultado(reserva, pago, resultado(EstadoPayment.RECHAZADO, "7500", "mp-viejo", 1L));

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.EN_PROCESO);
            assertThat(pago.getExternalPaymentId()).isEqualTo("mp-nuevo");
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class AvisosAlAdmin {

        private java.util.List<AvisoReserva> publicados() {
            ArgumentCaptor<AvisoReserva> captor = ArgumentCaptor.forClass(AvisoReserva.class);
            verify(eventos, org.mockito.Mockito.atLeast(0)).publishEvent(captor.capture());
            return captor.getAllValues();
        }

        @Test
        void pagarLaSeniaAvisaReservaConfirmada() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL, null, empleado.getEmail());

            assertThat(publicados()).singleElement().satisfies(a -> {
                assertThat(a.tipo()).isEqualTo(TipoAviso.RESERVA_CONFIRMADA);
                assertThat(a.texto()).contains("Pagó $7.500 de un total de $15.000");
            });
        }

        @Test
        void cobrarElSaldoAvisaSaldoCobrado() {
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            pago(1L, reserva, EstadoPago.APROBADO, "7500");

            cobroService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL, null, empleado.getEmail());

            assertThat(publicados()).extracting(AvisoReserva::tipo).containsExactly(TipoAviso.SALDO_COBRADO);
        }

        @Test
        void unPagoQueNoLlegaALaSeniaNoAvisa() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            cobroService.registrarPagoManual(10L, new BigDecimal("3000"), PaymentProvider.MANUAL, null, empleado.getEmail());

            assertThat(publicados()).isEmpty();
        }

        @Test
        void unWebhookRepetidoNoRepiteElAviso() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");

            pagoOnlineService.aplicarResultado(reserva, pago, resultado(EstadoPayment.APROBADO, "7500", "mp-1", 1L));
            pagoOnlineService.aplicarResultado(reserva, pago, resultado(EstadoPayment.APROBADO, "7500", "mp-1", 1L));

            assertThat(publicados()).extracting(AvisoReserva::tipo).containsExactly(TipoAviso.RESERVA_CONFIRMADA);
        }

        @Test
        void unCobroPorOtroMontoAvisaQueHayQueDevolver() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");

            pagoOnlineService.aplicarResultado(reserva, pago, resultado(EstadoPayment.APROBADO, "1", "mp-1", 1L));

            assertThat(publicados()).singleElement().satisfies(a -> {
                assertThat(a.tipo()).isEqualTo(TipoAviso.REEMBOLSO_MANUAL);
                assertThat(a.texto()).contains("devolver $7.500").contains("cobró $1");
            });
        }

        @Test
        void unaDevolucionTotalDesdeMercadoPagoAvisaLaCancelacion() {
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            Pago pago = pago(1L, reserva, EstadoPago.APROBADO, "7500");
            pago.setExternalPaymentId("mp-1");

            pagoOnlineService.aplicarResultado(reserva, pago, resultado(EstadoPayment.DEVUELTO, "7500", "mp-1", 1L));

            assertThat(publicados()).extracting(AvisoReserva::tipo).containsExactly(TipoAviso.RESERVA_CANCELADA);
        }

        @Test
        void unReembolsoPendienteQueSaleAvisaQueSeHizo() {
            Reserva reserva = reservaBloqueada(EstadoReserva.CANCELADA);
            Pago pendiente = pago(1L, reserva, EstadoPago.REEMBOLSO_PENDIENTE, "15000");
            pendiente.setExternalPaymentId("mp-1");
            pendiente.setMontoReembolsado(new BigDecimal("15000"));
            pendiente.setReembolsoSolicitadoAt(AHORA.minusMinutes(5));
            when(pagoRepository.findReservaIdByPagoId(1L)).thenReturn(Optional.of(10L));
            when(mercadoPago.consultarPago("mp-1")).thenReturn(resultado(EstadoPayment.APROBADO, "15000", "mp-1", 1L));

            reembolsoService.reintentarReembolso(1L);

            assertThat(publicados()).singleElement().satisfies(a -> {
                assertThat(a.tipo()).isEqualTo(TipoAviso.REEMBOLSO_REALIZADO);
                assertThat(a.texto()).contains("devolvió $15.000");
            });
        }

        @Test
        void elWebhookDeUnReembolsoPendienteAvisaQueSeHizo() {
            Reserva reserva = reservaBloqueada(EstadoReserva.CANCELADA);
            Pago pendiente = pago(1L, reserva, EstadoPago.REEMBOLSO_PENDIENTE, "15000");
            pendiente.setExternalPaymentId("mp-1");
            pendiente.setMontoReembolsado(new BigDecimal("15000"));

            pagoOnlineService.aplicarResultado(reserva, pendiente, resultado(EstadoPayment.DEVUELTO, "15000", "mp-1", 1L));

            assertThat(publicados()).extracting(AvisoReserva::tipo).containsExactly(TipoAviso.REEMBOLSO_REALIZADO);
        }

        @Test
        void unReembolsoQueVencioElPlazoAvisaAlAdmin() {
            Reserva reserva = reservaBloqueada(EstadoReserva.CANCELADA);
            Pago pendiente = pago(1L, reserva, EstadoPago.REEMBOLSO_PENDIENTE, "15000");
            pendiente.setExternalPaymentId("mp-1");
            pendiente.setMontoReembolsado(new BigDecimal("15000"));
            pendiente.setReembolsoSolicitadoAt(AHORA.minusHours(25));
            when(pagoRepository.findReservaIdByPagoId(1L)).thenReturn(Optional.of(10L));
            when(mercadoPago.consultarPago("mp-1")).thenReturn(resultado(EstadoPayment.APROBADO, "15000", "mp-1", 1L));
            doThrow(new ReembolsoFallidoException(false)).when(mercadoPago).reembolsar(any(), any());

            reembolsoService.reintentarReembolso(1L);

            assertThat(publicados()).extracting(AvisoReserva::tipo).containsExactly(TipoAviso.REEMBOLSO_MANUAL);
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Test
    void emailInexistenteNoLlegaAlServicio() {
        when(usuarioService.autenticado("nadie@test.com"))
                .thenThrow(new ReservaException("Usuario no encontrado"));

        assertThatThrownBy(() -> pagoOnlineService.iniciarPago(10L, false, "nadie@test.com"))
                .isInstanceOf(ReservaException.class);
        verify(reservaService, never()).bloquearConAcceso(anyLong(), any());
        verify(mercadoPago, never()).crearPreferencia(any());
        verify(usuarioService, never()).findByEmail(anyString());
    }
}
