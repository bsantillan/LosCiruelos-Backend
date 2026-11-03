package com.LosCiruelos.padel_club_api.pagos.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CobroQrResponse;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.exception.CobroQrNoConfiguradoException;
import com.LosCiruelos.padel_club_api.pagos.exception.ReembolsoFallidoException;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.EstadoOrden;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.EstadoOrdenQr;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr.OrdenNoCancelableException;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;

/** Cobro en el club con el QR de la caja. Hoy es el 13/10/2026 a las 10:00; la reserva es de $15.000. */
class CobroQrServiceTest extends PagosTestBase {

    private static final String ORDER = "ORD01JQ4S4KY8HWQ6NA5PXB65B3D3";
    private Reserva reserva;

    @BeforeEach
    void unaReserva() {
        reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
        when(proveedorQr.crear(any(), anyString(), anyInt())).thenReturn(ORDER);
        when(pagoRepository.findReservaIdByOrderId(ORDER)).thenReturn(Optional.of(10L));
        when(pagoRepository.findReservaIdByPagoId(100L)).thenReturn(Optional.of(10L));
    }

    private CobroQrResponse generar(String monto) {
        return cobroQrService.generar(10L, monto != null ? new BigDecimal(monto) : null, empleado.getEmail());
    }

    private Pago qr() {
        return reserva.getPagos().stream().filter(Pago::esCobroQr).findFirst().orElseThrow();
    }

    private void laOrderQueda(EstadoOrden estado, String montoPagado) {
        when(proveedorQr.consultar(ORDER)).thenReturn(new EstadoOrdenQr(estado,
                montoPagado != null ? new BigDecimal(montoPagado) : null, "PAY01J67CQQH5904WDBVZEM4JMEP3"));
    }

    @Nested
    class Generar {

        @Test
        void sinMontoEsPorTodoLoQueFaltaYQuedaEnProceso() {
            CobroQrResponse r = generar(null);

            assertThat(r.orderId()).isEqualTo(ORDER);
            assertThat(r.monto()).isEqualTo(new BigDecimal("15000.00"));
            assertThat(r.venceAt()).isEqualTo(AHORA.plusMinutes(10));
            assertThat(r.estado()).isEqualTo(EstadoPago.EN_PROCESO);
            Pago pago = qr();
            assertThat(pago.getProvider()).isEqualTo(PaymentProvider.MERCADO_PAGO);
            assertThat(pago.getRegistradoPorId()).isEqualTo(empleado.getId());
            // mientras el QR está activo no se puede cobrar de otra forma
            assertThat(reserva.tienePagoEnProceso()).isTrue();
            verify(proveedorQr).crear(eq(pago), contains("cancha 1"), eq(10));
        }

        @Test
        void conUnQrActivoPorElMismoMontoDevuelveEseSinCrearOtro() {
            generar("7500");
            CobroQrResponse otra = generar("7500");

            assertThat(otra.orderId()).isEqualTo(ORDER);
            verify(proveedorQr, times(1)).crear(any(), anyString(), anyInt());
        }

        @Test
        void conUnQrActivoPorOtroMontoHayQueCancelarloAntes() {
            generar("7500");

            assertThatThrownBy(() -> generar(null))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("Ya hay un QR activo para esta reserva: cancelalo antes de generar otro.");
        }

        @Test
        void noPuedeSuperarElPendiente() {
            assertThatThrownBy(() -> generar("15000.01"))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("supera el pendiente");
            verify(proveedorQr, never()).crear(any(), anyString(), anyInt());
        }

        @Test
        void sinConfigurarNoSeOfrece() {
            when(proveedorQr.estaConfigurado()).thenReturn(false);

            assertThatThrownBy(() -> generar(null)).isInstanceOf(CobroQrNoConfiguradoException.class);
        }

        @Test
        void unQrVencidoSinResolverSeResuelveAntesDeGenerarOtro() {
            Pago viejo = Pago.builder().id(99L).monto(new BigDecimal("7500")).estado(EstadoPago.EN_PROCESO)
                    .provider(PaymentProvider.MERCADO_PAGO).createdAt(AHORA.minusMinutes(20))
                    .venceAt(AHORA.minusMinutes(10)).orderId(ORDER).build();
            reserva.agregarPago(viejo);
            laOrderQueda(EstadoOrden.VENCIDA, null);
            when(proveedorQr.crear(any(), anyString(), anyInt())).thenReturn("ORD-NUEVA");

            CobroQrResponse r = generar(null);

            assertThat(viejo.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
            assertThat(r.orderId()).isEqualTo("ORD-NUEVA");
        }
    }

    @Nested
    class UnSoloQrEnLaCaja {

        private Pago qrDeOtraReserva(java.time.LocalDateTime venceAt) {
            Reserva otra = com.LosCiruelos.padel_club_api.TestData.reserva(20L, cliente, EstadoReserva.SEÑADA, "15000");
            Pago pago = Pago.builder().id(77L).reserva(otra).monto(new BigDecimal("7500"))
                    .estado(EstadoPago.EN_PROCESO).provider(PaymentProvider.MERCADO_PAGO)
                    .createdAt(venceAt.minusMinutes(10)).venceAt(venceAt).orderId("ORD-OTRA").build();
            otra.agregarPago(pago);
            when(reservaService.bloquear(20L)).thenReturn(otra);
            when(pagoRepository.findQrEnCurso(EstadoPago.EN_PROCESO)).thenReturn(List.of(pago));
            return pago;
        }

        @Test
        void siLaCajaEsperaElPagoDeOtraReservaHayQueEsperar() {
            qrDeOtraReserva(AHORA.plusMinutes(4));

            assertThatThrownBy(() -> generar(null))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("El QR de la caja está esperando el pago de $7.500 de la reserva #20 hasta las 10:04: "
                            + "esperá a que se pague o cancelalo.");
            verify(proveedorQr, never()).crear(any(), anyString(), anyInt());
        }

        @Test
        void unQrVencidoDeOtraReservaSeResuelveYLaCajaQuedaLibre() {
            Pago otro = qrDeOtraReserva(AHORA.minusMinutes(1));
            when(proveedorQr.consultar("ORD-OTRA")).thenReturn(new EstadoOrdenQr(EstadoOrden.VENCIDA, null, null));

            CobroQrResponse r = generar(null);

            assertThat(otro.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
            assertThat(r.estado()).isEqualTo(EstadoPago.EN_PROCESO);
        }

        @Test
        void siOtroEmpleadoOcupaLaCajaEnElMismoMomentoEs409() {
            when(pagoRepository.save(any(Pago.class)))
                    .thenThrow(new org.springframework.dao.DataIntegrityViolationException("uk_pagos_qr_en_curso"));

            assertThatThrownBy(() -> generar(null))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("El QR de la caja está esperando otro cobro: esperá a que se pague o cancelalo.");
        }
    }

    @Nested
    class Notificacion {

        @Test
        void conLaOrderPagadaPorElMontoExactoQuedaAprobado() {
            generar(null);
            laOrderQueda(EstadoOrden.PROCESADA, "15000.00");

            cobroQrService.notificacion(ORDER);

            assertThat(qr().getEstado()).isEqualTo(EstadoPago.APROBADO);
            assertThat(qr().getPaidAt()).isEqualTo(AHORA);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
            assertThat(reserva.getMontoPendiente()).isEqualByComparingTo("0");
        }

        @Test
        void unaNotificacionRepetidaNoHaceNada() {
            generar(null);
            laOrderQueda(EstadoOrden.PROCESADA, "15000.00");

            cobroQrService.notificacion(ORDER);
            cobroQrService.notificacion(ORDER);

            verify(proveedorQr, times(1)).consultar(ORDER);
            assertThat(reserva.getPagos()).hasSize(1);
            assertThat(reserva.getMontoPagado()).isEqualByComparingTo("15000");
        }

        @Test
        void siMercadoPagoCobroOtroMontoQuedaParaDevolver() {
            generar("7500");
            laOrderQueda(EstadoOrden.PROCESADA, "750.00");

            cobroQrService.notificacion(ORDER);

            assertThat(qr().getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
            assertThat(qr().getMontoReembolsado()).isEqualByComparingTo("7500");
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
        }

        @Test
        void unaOrderVencidaOCanceladaNoCobraNada() {
            generar(null);
            laOrderQueda(EstadoOrden.VENCIDA, null);

            cobroQrService.notificacion(ORDER);

            assertThat(qr().getEstado()).isEqualTo(EstadoPago.EXPIRADO);
            assertThat(reserva.tienePagoEnProceso()).isFalse();
            assertThat(reserva.getMontoPagado()).isEqualByComparingTo("0");
        }

        @Test
        void unaOrderQueSigueEsperandoNoCambiaNada() {
            generar(null);
            laOrderQueda(EstadoOrden.CREADA, null);

            cobroQrService.notificacion(ORDER);

            assertThat(qr().getEstado()).isEqualTo(EstadoPago.EN_PROCESO);
        }

        @Test
        void siLaReservaYaNoEstaActivaElCobroQuedaParaDevolver() {
            generar(null);
            reserva.setEstado(EstadoReserva.CANCELADA);
            laOrderQueda(EstadoOrden.PROCESADA, "15000.00");

            cobroQrService.notificacion(ORDER);

            assertThat(qr().getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        }

        @Test
        void unaOrderDesconocidaSeIgnora() {
            when(pagoRepository.findReservaIdByOrderId("ORD-OTRA")).thenReturn(Optional.empty());

            cobroQrService.notificacion("ORD-OTRA");

            verify(proveedorQr, never()).consultar(anyString());
        }
    }

    @Nested
    class Cancelar {

        @Test
        void cancelaLaOrderYElPagoQuedaVencido() {
            generar(null);

            CobroQrResponse r = cobroQrService.cancelar(10L, empleado.getEmail());

            verify(proveedorQr).cancelar(qr());
            assertThat(r.estado()).isEqualTo(EstadoPago.EXPIRADO);
            assertThat(reserva.tienePagoEnProceso()).isFalse();
        }

        @Test
        void siYaSePagoNoSeCancelaYSeRegistraElCobro() {
            generar(null);
            doThrow(new OrdenNoCancelableException("order_already_canceled")).when(proveedorQr).cancelar(any());
            laOrderQueda(EstadoOrden.PROCESADA, "15000.00");

            CobroQrResponse r = cobroQrService.cancelar(10L, empleado.getEmail());

            assertThat(r.estado()).isEqualTo(EstadoPago.APROBADO);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
        }

        @Test
        void sinQrActivoEs409() {
            assertThatThrownBy(() -> cobroQrService.cancelar(10L, empleado.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessage("No hay un QR activo para esta reserva.");
        }
    }

    @Nested
    class JobDeRespaldo {

        @Test
        void buscaLosQrVencidosConUnMinutoDeMargen() {
            when(pagoRepository.findIdsQrVencidos(EstadoPago.EN_PROCESO, AHORA.minusSeconds(60)))
                    .thenReturn(List.of(100L));

            assertThat(cobroQrService.idsQrVencidos()).containsExactly(100L);
        }

        @Test
        void revisaUnQrQueNoTuvoNotificacion() {
            generar(null);
            laOrderQueda(EstadoOrden.PROCESADA, "15000.00");

            assertThat(cobroQrService.revisar(100L)).isEqualTo(EstadoPago.APROBADO);
            // otra vuelta del job: ya no está EN_PROCESO
            assertThat(cobroQrService.revisar(100L)).isEqualTo(EstadoPago.APROBADO);
            verify(proveedorQr, times(1)).consultar(ORDER);
        }
    }

    @Nested
    class Reembolso {

        private void cobrado() {
            generar(null);
            laOrderQueda(EstadoOrden.PROCESADA, "15000.00");
            cobroQrService.notificacion(ORDER);
        }

        @Test
        void unCobroConQrSeDevuelvePorLaApiDeOrders() {
            cobrado();

            reembolsoService.reembolsar(reserva, new BigDecimal("7500"));

            verify(proveedorQr).reembolsar(qr(), new BigDecimal("7500"));
            verify(mercadoPago, never()).reembolsar(any(), any());
            assertThat(qr().getEstado()).isEqualTo(EstadoPago.DEVUELTO);
            assertThat(qr().getMontoReembolsado()).isEqualByComparingTo("7500");
        }

        @Test
        void siMercadoPagoNoLoAceptaSeReintentaYSeConsultaLaOrderAntes() {
            cobrado();
            doThrow(new ReembolsoFallidoException(false)).when(proveedorQr).reembolsar(any(), any());

            reembolsoService.reembolsar(reserva, new BigDecimal("15000"));
            assertThat(qr().getEstado()).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);

            laOrderQueda(EstadoOrden.DEVUELTA, "15000.00");
            assertThat(reembolsoService.reintentarReembolso(100L)).isEqualTo(EstadoPago.DEVUELTO);
        }

        @Test
        void unRechazoDefinitivoQuedaParaDevolverAMano() {
            cobrado();
            doThrow(new ReembolsoFallidoException(true)).when(proveedorQr).reembolsar(any(), any());

            reembolsoService.reembolsar(reserva, new BigDecimal("15000"));

            assertThat(qr().getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
        }
    }
}
