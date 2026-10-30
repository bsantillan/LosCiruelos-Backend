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
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.EstadoPayment;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentResult;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.pagos.exception.PagoNoEncontradoException;
import com.LosCiruelos.padel_club_api.pagos.exception.PagoProveedorException;
import com.LosCiruelos.padel_club_api.pagos.exception.ReembolsoFallidoException;

/** Reembolsos por cancelación y sus reintentos. */
class ReembolsoServiceTest extends PagosTestBase {

    @Nested
    class ReembolsoPorCancelacion {

        @Test
        void primeroDevuelveLoCobradoOnlineYDespuesLoDelClub() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PAGADA);
            Pago enElClub = pago(1L, reserva, EstadoPago.APROBADO, "7500");
            enElClub.setExternalPaymentId(null);
            Pago online = pago(2L, reserva, EstadoPago.APROBADO, "7500");
            online.setExternalPaymentId("mp-2");

            reembolsoService.reembolsar(reserva, new BigDecimal("10000"));

            verify(mercadoPago).reembolsar(online, new BigDecimal("7500"));
            assertThat(online.getEstado()).isEqualTo(EstadoPago.DEVUELTO);
            assertThat(online.getMontoReembolsado()).isEqualByComparingTo("7500");
            assertThat(enElClub.getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
            assertThat(enElClub.getMontoReembolsado()).isEqualByComparingTo("2500");
            // fecha del reembolso para el cierre de caja
            assertThat(online.getReembolsadoAt()).isEqualTo(AHORA);
            assertThat(enElClub.getReembolsadoAt()).isEqualTo(AHORA);
            // el club se queda con 5000
            assertThat(reserva.getMontoPagado()).isEqualByComparingTo("5000");
        }

        @Test
        void unReembolsoParcialDevuelveSoloEsaParte() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PAGADA);
            Pago online = pago(1L, reserva, EstadoPago.APROBADO, "15000");
            online.setExternalPaymentId("mp-1");

            reembolsoService.reembolsar(reserva, new BigDecimal("7500"));

            verify(mercadoPago).reembolsar(online, new BigDecimal("7500"));
            assertThat(online.getEstado()).isEqualTo(EstadoPago.DEVUELTO);
            assertThat(reserva.getMontoPagado()).isEqualByComparingTo("7500");
        }

        @Test
        void siMercadoPagoNoAceptaElReembolsoQuedaPendienteSinCortarLaCancelacion() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PAGADA);
            Pago online = pago(1L, reserva, EstadoPago.APROBADO, "15000");
            online.setExternalPaymentId("mp-1");
            doThrow(new ReembolsoFallidoException(false)).when(mercadoPago).reembolsar(any(), any());

            reembolsoService.reembolsar(reserva, new BigDecimal("15000"));

            assertThat(online.getEstado()).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);
            assertThat(online.getMontoReembolsado()).isEqualByComparingTo("15000");
            assertThat(online.getReembolsoSolicitadoAt()).isEqualTo(AHORA);
            // para la reserva ya cuenta como devuelto
            assertThat(reserva.getMontoPagado()).isEqualByComparingTo("0");
        }

        @Test
        void siElPagoNoExisteEnMercadoPagoQuedaParaDevolverAMano() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PAGADA);
            Pago online = pago(1L, reserva, EstadoPago.APROBADO, "15000");
            online.setExternalPaymentId("mp-1");
            doThrow(new ReembolsoFallidoException(true)).when(mercadoPago).reembolsar(any(), any());

            reembolsoService.reembolsar(reserva, new BigDecimal("15000"));

            assertThat(online.getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class ReintentoDeReembolso {

        private Reserva reserva;
        private Pago pendiente;

        @BeforeEach
        void reembolsoPendiente() {
            reserva = reservaBloqueada(EstadoReserva.CANCELADA);
            pendiente = pago(1L, reserva, EstadoPago.REEMBOLSO_PENDIENTE, "15000");
            pendiente.setExternalPaymentId("mp-1");
            pendiente.setMontoReembolsado(new BigDecimal("7500"));
            pendiente.setReembolsoSolicitadoAt(AHORA.minusMinutes(10));
            when(pagoRepository.findReservaIdByPagoId(1L)).thenReturn(Optional.of(10L));
            when(mercadoPago.consultarPago("mp-1")).thenReturn(resultado(EstadoPayment.APROBADO, "15000", "mp-1", 1L));
        }

        @Test
        void siMercadoPagoLoAceptaQuedaDevueltoConLaMismaParte() {
            assertThat(reembolsoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.DEVUELTO);

            verify(mercadoPago).reembolsar(pendiente, new BigDecimal("7500"));
            assertThat(pendiente.getMontoReembolsado()).isEqualByComparingTo("7500");
        }

        @Test
        void siMercadoPagoYaLoTieneDevueltoNoSeDevuelveDosVeces() {
            PaymentResult devuelto = resultado(EstadoPayment.APROBADO, "15000", "mp-1", 1L);
            devuelto.setMontoDevuelto(new BigDecimal("7500"));
            when(mercadoPago.consultarPago("mp-1")).thenReturn(devuelto);

            assertThat(reembolsoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.DEVUELTO);
            verify(mercadoPago, never()).reembolsar(any(), any());
        }

        @Test
        void siSeDevolvioOtroMontoDesdeElPanelQuedaParaRevisarAMano() {
            PaymentResult devuelto = resultado(EstadoPayment.APROBADO, "15000", "mp-1", 1L);
            devuelto.setMontoDevuelto(new BigDecimal("15000"));
            when(mercadoPago.consultarPago("mp-1")).thenReturn(devuelto);

            assertThat(reembolsoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.A_REEMBOLSAR);
            verify(mercadoPago, never()).reembolsar(any(), any());
        }

        @Test
        void unaFallaTransitoriaSigueEsperandoElProximoIntento() {
            doThrow(new ReembolsoFallidoException(false)).when(mercadoPago).reembolsar(any(), any());

            assertThat(reembolsoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);
        }

        @Test
        void siMercadoPagoNoRespondeLaConsultaSeReintentaDespues() {
            when(mercadoPago.consultarPago("mp-1")).thenThrow(new PagoProveedorException("MP caído"));

            assertThat(reembolsoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);
        }

        @Test
        void pasadas24HorasQuedaParaDevolverAMano() {
            pendiente.setReembolsoSolicitadoAt(AHORA.minusHours(24));
            doThrow(new ReembolsoFallidoException(false)).when(mercadoPago).reembolsar(any(), any());

            assertThat(reembolsoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.A_REEMBOLSAR);
        }

        @Test
        void siElPagoNoExisteEnMercadoPagoQuedaParaDevolverAMano() {
            when(mercadoPago.consultarPago("mp-1")).thenThrow(new PagoNoEncontradoException("no existe"));

            assertThat(reembolsoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.A_REEMBOLSAR);
        }

        @Test
        void siOtroProcesoYaLoResolvioNoHaceNada() {
            pendiente.setEstado(EstadoPago.DEVUELTO);

            assertThat(reembolsoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.DEVUELTO);
            verify(mercadoPago, never()).consultarPago(anyString());
        }

        @Test
        void elWebhookDeDevolucionLoResuelve() {
            pagoOnlineService.aplicarResultado(reserva, pendiente, resultado(EstadoPayment.DEVUELTO, "15000", "mp-1", 1L));

            assertThat(pendiente.getEstado()).isEqualTo(EstadoPago.DEVUELTO);
            assertThat(pendiente.getMontoReembolsado()).isEqualByComparingTo("7500");
        }

        @Test
        void unAprobadoRepetidoNoLoVuelveAtras() {
            pagoOnlineService.aplicarResultado(reserva, pendiente, resultado(EstadoPayment.APROBADO, "15000", "mp-1", 1L));

            assertThat(pendiente.getEstado()).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);
        }
    }
}
