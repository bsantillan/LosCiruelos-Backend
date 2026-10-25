package com.LosCiruelos.padel_club_api.Services;

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

import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.DTOs.PaymentResult;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PagoResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ReservaResponse;
import com.LosCiruelos.padel_club_api.Entities.Pago;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPagoExterno;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.PaymentProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.PagoNoEncontradoException;
import com.LosCiruelos.padel_club_api.Exceptions.PagoProveedorException;
import com.LosCiruelos.padel_club_api.Exceptions.ReembolsoFallidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.Repository.PagoRepository;
import com.LosCiruelos.padel_club_api.Services.Avisos.AvisoReserva;
import com.LosCiruelos.padel_club_api.Services.Avisos.TipoAviso;
import com.LosCiruelos.padel_club_api.Services.Pagos.PaymentService;
import com.LosCiruelos.padel_club_api.Services.Pagos.PaymentServiceFactory;

class PagoServiceTest {

    private PaymentService mercadoPago;
    private ReservaService reservaService;
    private PagoRepository pagoRepository;
    private UsuarioService usuarioService;
    private PagoService pagoService;
    private ApplicationEventPublisher eventos;

    private Usuario cliente;
    private Usuario empleado;

    @BeforeEach
    void setUp() {
        mercadoPago = mock(PaymentService.class);
        PaymentServiceFactory factory = mock(PaymentServiceFactory.class);
        when(factory.getService()).thenReturn(mercadoPago);

        ConfiguracionRepository configuracionRepository = mock(ConfiguracionRepository.class);
        when(configuracionRepository.obtener()).thenReturn(TestData.configuracion());

        reservaService = mock(ReservaService.class);
        pagoRepository = mock(PagoRepository.class);
        usuarioService = mock(UsuarioService.class);

        // save devuelve el mismo pago y le asigna id si es nuevo (como IDENTITY)
        when(pagoRepository.save(any(Pago.class))).thenAnswer(inv -> {
            Pago p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(100L);
            }
            return p;
        });

        cliente = usuario(1L, Role.CLIENTE);
        empleado = usuario(2L, Role.EMPLEADO);
        when(usuarioService.findByEmailOrThrow(eq(cliente.getEmail()), any())).thenReturn(cliente);
        when(usuarioService.findByEmailOrThrow(eq(empleado.getEmail()), any())).thenReturn(empleado);

        pagoService = new PagoService(factory, reservaService, pagoRepository,
                new ConfiguracionService(configuracionRepository), usuarioService, mock(ProductoService.class),
                TestData.reloj(AHORA), eventos = mock(ApplicationEventPublisher.class));
    }

    private Reserva reservaBloqueada(EstadoReserva estado) {
        Reserva reserva = reserva(10L, cliente, estado, "15000");
        when(reservaService.bloquearConAcceso(eq(10L), any())).thenReturn(reserva);
        when(reservaService.bloquear(10L)).thenReturn(reserva);
        return reserva;
    }

    private static PaymentResult resultado(EstadoPagoExterno estado, String monto, String paymentId, Long pagoId) {
        return PaymentResult.builder()
                .estado(estado)
                .monto(monto != null ? new BigDecimal(monto) : null)
                .paymentId(paymentId)
                .pagoId(pagoId)
                .externalReference("10")
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class PagoManual {

        @Test
        void pagarLaSeniaDejaLaReservaSenada() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            PagoResponse r = pagoService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL,
                    empleado.getEmail());

            assertThat(r.getEstado()).isEqualTo(EstadoPago.APROBADO);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
        }

        @Test
        void pagarElTotalEnUnSoloPagoDejaLaReservaPagada() {
            // antes quedaba PARCIALMENTE_PAGADA porque la lista de pagos en memoria no incluía el nuevo
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            pagoService.registrarPagoManual(10L, new BigDecimal("15000"), PaymentProvider.MANUAL, empleado.getEmail());

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
            assertThat(reserva.getMontoPendiente()).isEqualByComparingTo("0");
        }

        @Test
        void menosQueLaSeniaQuedaParcialYLuegoSePuedeCompletar() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            pagoService.registrarPagoManual(10L, new BigDecimal("3000"), PaymentProvider.MANUAL, empleado.getEmail());
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PARCIALMENTE_PAGADA);

            // antes una reserva PARCIALMENTE_PAGADA no admitía más pagos
            pagoService.registrarPagoManual(10L, new BigDecimal("12000"), PaymentProvider.MANUAL, empleado.getEmail());
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
        }

        @Test
        void montoMayorAlPendienteSeRechaza() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            assertThatThrownBy(() -> pagoService.registrarPagoManual(10L, new BigDecimal("15000.01"),
                    PaymentProvider.MANUAL, empleado.getEmail()))
                    .isInstanceOf(ReservaException.class)
                    .hasMessageContaining("supera el pendiente");
        }

        @Test
        void montoCeroONegativoSeRechaza() {
            assertThatThrownBy(() -> pagoService.registrarPagoManual(10L, new BigDecimal("-5000"),
                    PaymentProvider.MANUAL, empleado.getEmail()))
                    .isInstanceOf(ReservaException.class);
            assertThatThrownBy(() -> pagoService.registrarPagoManual(10L, null,
                    PaymentProvider.MANUAL, empleado.getEmail()))
                    .isInstanceOf(ReservaException.class);
        }

        @Test
        void noSeAceptaSiHayUnPagoOnlineEnProceso() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            pago(1L, reserva, EstadoPago.EN_PROCESO, "7500");

            assertThatThrownBy(() -> pagoService.registrarPagoManual(10L, new BigDecimal("7500"),
                    PaymentProvider.MANUAL, empleado.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class);
        }

        @Test
        void unPagoOnlineIniciadoSeDescarta() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago iniciado = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");

            pagoService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL, empleado.getEmail());

            assertThat(iniciado.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
        }

        @Test
        void reservaVencidaNoAdmitePagos() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            reserva.setExpiresAt(AHORA.minusMinutes(1));

            assertThatThrownBy(() -> pagoService.registrarPagoManual(10L, new BigDecimal("7500"),
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

            pagoService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL, empleado.getEmail());

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

            pagoService.registrarPagoManual(10L, new BigDecimal("15000"), PaymentProvider.MANUAL, empleado.getEmail());

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
        }

        @Test
        void unaReservaCompletadaYaNoAdmitePagos() {
            reservaBloqueada(EstadoReserva.COMPLETADA);

            assertThatThrownBy(() -> pagoService.registrarPagoManual(10L, new BigDecimal("1"),
                    PaymentProvider.MANUAL, empleado.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class IniciarPago {

        @Test
        void seniaCobraElPorcentajeConfigurado() {
            reservaBloqueada(EstadoReserva.PENDIENTE);
            when(mercadoPago.crearPreferencia(any())).thenReturn("pref-1");

            PagoResponse r = pagoService.iniciarPago(10L, false, cliente.getEmail());

            assertThat(r.getMonto()).isEqualByComparingTo("7500");
            assertThat(r.getPaymentToken()).isEqualTo("pref-1");
            assertThat(r.getEstado()).isEqualTo(EstadoPago.PENDIENTE);
        }

        @Test
        void totalCobraTodoLoPendiente() {
            reservaBloqueada(EstadoReserva.PENDIENTE);
            when(mercadoPago.crearPreferencia(any())).thenReturn("pref-1");

            assertThat(pagoService.iniciarPago(10L, true, cliente.getEmail()).getMonto()).isEqualByComparingTo("15000");
        }

        @Test
        void reservaSenadaPuedePagarElSaldoOnline() {
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            pago(1L, reserva, EstadoPago.APROBADO, "7500");
            when(mercadoPago.crearPreferencia(any())).thenReturn("pref-2");

            assertThat(pagoService.iniciarPago(10L, true, cliente.getEmail()).getMonto()).isEqualByComparingTo("7500");
            assertThatThrownBy(() -> pagoService.iniciarPago(10L, false, cliente.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class)
                    .hasMessageContaining("seña ya está paga");
        }

        @Test
        void siYaHayUnPagoIniciadoPorElMismoMontoSeReutiliza() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago iniciado = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");
            iniciado.setPaymentToken("pref-existente");

            PagoResponse r = pagoService.iniciarPago(10L, false, cliente.getEmail());

            assertThat(r.getId()).isEqualTo(1L);
            verify(mercadoPago, never()).crearPreferencia(any());
        }

        @Test
        void unPagoIniciadoPorOtroMontoSeDescarta() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago iniciado = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");
            iniciado.setPaymentToken("pref-seña");
            when(mercadoPago.crearPreferencia(any())).thenReturn("pref-total");

            PagoResponse r = pagoService.iniciarPago(10L, true, cliente.getEmail());

            assertThat(iniciado.getEstado()).isEqualTo(EstadoPago.EXPIRADO);
            assertThat(r.getMonto()).isEqualByComparingTo("15000");
        }

        @Test
        void noSePuedeIniciarConUnPagoEnProceso() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            pago(1L, reserva, EstadoPago.EN_PROCESO, "7500");

            assertThatThrownBy(() -> pagoService.iniciarPago(10L, false, cliente.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class);
        }

        @Test
        void reservaCanceladaNoAdmitePagos() {
            reservaBloqueada(EstadoReserva.CANCELADA);

            assertThatThrownBy(() -> pagoService.iniciarPago(10L, false, cliente.getEmail()))
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
            when(mercadoPago.cobrar(any(), anyMap())).thenReturn(resultado(EstadoPagoExterno.APROBADO, "7500", "mp-1", 1L));

            pagoService.procesarPago(10L, Map.of("transaction_amount", 1, "token", "tok"), cliente.getEmail());

            ArgumentCaptor<Pago> cobrado = ArgumentCaptor.forClass(Pago.class);
            verify(mercadoPago).cobrar(cobrado.capture(), anyMap());
            assertThat(cobrado.getValue().getMonto()).isEqualByComparingTo("7500");
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
        }

        @Test
        void pagoRechazadoDejaLaReservaPendiente() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");
            when(mercadoPago.cobrar(any(), anyMap())).thenReturn(resultado(EstadoPagoExterno.RECHAZADO, "7500", "mp-1", 1L));

            PagoResponse r = pagoService.procesarPago(10L, Map.of(), cliente.getEmail());

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.RECHAZADO);
            assertThat(pago.getExternalPaymentId()).isEqualTo("mp-1");
            assertThat(r.getEstadoReserva()).isEqualTo(EstadoReserva.PENDIENTE);
        }

        @Test
        void pagoEnProcesoQuedaEnProcesoHastaElWebhook() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");
            when(mercadoPago.cobrar(any(), anyMap())).thenReturn(resultado(EstadoPagoExterno.EN_PROCESO, "7500", "mp-1", 1L));

            pagoService.procesarPago(10L, Map.of(), cliente.getEmail());

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.EN_PROCESO);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PENDIENTE);
            assertThat(reserva.tienePagoActivo()).isTrue();
        }

        @Test
        void sinPagoIniciadoNoSeCobra() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            assertThatThrownBy(() -> pagoService.procesarPago(10L, Map.of(), cliente.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class);
            verify(mercadoPago, never()).cobrar(any(), anyMap());
        }

        @Test
        void reservaCanceladaNoSeCobraAunqueTengaUnPagoIniciado() {
            Reserva reserva = reservaBloqueada(EstadoReserva.CANCELADA);
            pago(1L, reserva, EstadoPago.PENDIENTE, "7500");

            assertThatThrownBy(() -> pagoService.procesarPago(10L, Map.of(), cliente.getEmail()))
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
            when(mercadoPago.consultarPago("mp-1")).thenReturn(resultado(EstadoPagoExterno.APROBADO, "7500", "mp-1", 1L));
            when(pagoRepository.findReservaIdByExternalPaymentId("mp-1")).thenReturn(Optional.of(10L));

            pagoService.procesarNotificacion("mp-1");

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.APROBADO);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
        }

        @Test
        void pagoConWalletSinIdExternoSeEncuentraPorMetadata() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(7L, reserva, EstadoPago.PENDIENTE, "15000");
            when(mercadoPago.consultarPago("mp-9")).thenReturn(resultado(EstadoPagoExterno.APROBADO, "15000", "mp-9", 7L));
            when(pagoRepository.findReservaIdByExternalPaymentId("mp-9")).thenReturn(Optional.empty());
            when(pagoRepository.findReservaIdByPagoId(7L)).thenReturn(Optional.of(10L));

            pagoService.procesarNotificacion("mp-9");

            assertThat(pago.getExternalPaymentId()).isEqualTo("mp-9");
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
        }

        @Test
        void notificacionSinPagoAsociadoSeIgnora() {
            when(mercadoPago.consultarPago("mp-x")).thenReturn(resultado(EstadoPagoExterno.APROBADO, "1", "mp-x", null));
            when(pagoRepository.findReservaIdByExternalPaymentId("mp-x")).thenReturn(Optional.empty());

            pagoService.procesarNotificacion("mp-x");

            verify(reservaService, never()).bloquear(anyLong());
        }

        @Test
        void aprobacionSobreReservaCanceladaQuedaParaReembolsar() {
            // antes revivía la reserva cancelada y podía quedar superpuesta con otra
            Reserva reserva = reservaBloqueada(EstadoReserva.CANCELADA);
            Pago pago = pago(1L, reserva, EstadoPago.EN_PROCESO, "7500");

            pagoService.aplicarResultado(reserva, pago, resultado(EstadoPagoExterno.APROBADO, "7500", "mp-1", 1L));

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        }

        @Test
        void aprobacionConMontoDistintoQuedaParaReembolsar() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");

            pagoService.aplicarResultado(reserva, pago, resultado(EstadoPagoExterno.APROBADO, "1", "mp-1", 1L));

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

            pagoService.aplicarResultado(reserva, segundo, resultado(EstadoPagoExterno.RECHAZADO, "7500", "mp-2", 2L));

            assertThat(segundo.getEstado()).isEqualTo(EstadoPago.RECHAZADO);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
        }

        @Test
        void notificacionRepetidaNoCambiaNada() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");
            PaymentResult aprobado = resultado(EstadoPagoExterno.APROBADO, "7500", "mp-1", 1L);

            pagoService.aplicarResultado(reserva, pago, aprobado);
            var pagadoEn = pago.getPaidAt();
            pagoService.aplicarResultado(reserva, pago, aprobado);

            assertThat(pago.getPaidAt()).isEqualTo(pagadoEn);
            assertThat(reserva.getMontoPagado()).isEqualByComparingTo("7500");
        }

        @Test
        void unPagoDevueltoNoVuelveAAprobarse() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.DEVUELTO, "7500");

            pagoService.aplicarResultado(reserva, pago, resultado(EstadoPagoExterno.APROBADO, "7500", "mp-1", 1L));

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.DEVUELTO);
        }

        @Test
        void reembolsoTotalCancelaLaReserva() {
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            Pago pago = pago(1L, reserva, EstadoPago.APROBADO, "7500");
            pago.setExternalPaymentId("mp-1");

            pagoService.aplicarResultado(reserva, pago, resultado(EstadoPagoExterno.DEVUELTO, "7500", "mp-1", 1L));

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.DEVUELTO);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.CANCELADA);
        }

        @Test
        void rechazoDeOtroIntentoNoPisaUnPagoEnCurso() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.EN_PROCESO, "7500");
            pago.setExternalPaymentId("mp-nuevo");

            pagoService.aplicarResultado(reserva, pago, resultado(EstadoPagoExterno.RECHAZADO, "7500", "mp-viejo", 1L));

            assertThat(pago.getEstado()).isEqualTo(EstadoPago.EN_PROCESO);
            assertThat(pago.getExternalPaymentId()).isEqualTo("mp-nuevo");
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class ReembolsoPorCancelacion {

        @Test
        void primeroDevuelveLoCobradoOnlineYDespuesLoDelClub() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PAGADA);
            Pago enElClub = pago(1L, reserva, EstadoPago.APROBADO, "7500");
            enElClub.setExternalPaymentId(null);
            Pago online = pago(2L, reserva, EstadoPago.APROBADO, "7500");
            online.setExternalPaymentId("mp-2");

            pagoService.reembolsar(reserva, new BigDecimal("10000"));

            verify(mercadoPago).reembolsar(online, new BigDecimal("7500"));
            assertThat(online.getEstado()).isEqualTo(EstadoPago.DEVUELTO);
            assertThat(online.getMontoReembolsado()).isEqualByComparingTo("7500");
            assertThat(enElClub.getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
            assertThat(enElClub.getMontoReembolsado()).isEqualByComparingTo("2500");
            // el club se queda con 5000
            assertThat(reserva.getMontoPagado()).isEqualByComparingTo("5000");
        }

        @Test
        void unReembolsoParcialDevuelveSoloEsaParte() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PAGADA);
            Pago online = pago(1L, reserva, EstadoPago.APROBADO, "15000");
            online.setExternalPaymentId("mp-1");

            pagoService.reembolsar(reserva, new BigDecimal("7500"));

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

            pagoService.reembolsar(reserva, new BigDecimal("15000"));

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

            pagoService.reembolsar(reserva, new BigDecimal("15000"));

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
            when(mercadoPago.consultarPago("mp-1")).thenReturn(resultado(EstadoPagoExterno.APROBADO, "15000", "mp-1", 1L));
        }

        @Test
        void siMercadoPagoLoAceptaQuedaDevueltoConLaMismaParte() {
            assertThat(pagoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.DEVUELTO);

            verify(mercadoPago).reembolsar(pendiente, new BigDecimal("7500"));
            assertThat(pendiente.getMontoReembolsado()).isEqualByComparingTo("7500");
        }

        @Test
        void siMercadoPagoYaLoTieneDevueltoNoSeDevuelveDosVeces() {
            PaymentResult devuelto = resultado(EstadoPagoExterno.APROBADO, "15000", "mp-1", 1L);
            devuelto.setMontoDevuelto(new BigDecimal("7500"));
            when(mercadoPago.consultarPago("mp-1")).thenReturn(devuelto);

            assertThat(pagoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.DEVUELTO);
            verify(mercadoPago, never()).reembolsar(any(), any());
        }

        @Test
        void siSeDevolvioOtroMontoDesdeElPanelQuedaParaRevisarAMano() {
            PaymentResult devuelto = resultado(EstadoPagoExterno.APROBADO, "15000", "mp-1", 1L);
            devuelto.setMontoDevuelto(new BigDecimal("15000"));
            when(mercadoPago.consultarPago("mp-1")).thenReturn(devuelto);

            assertThat(pagoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.A_REEMBOLSAR);
            verify(mercadoPago, never()).reembolsar(any(), any());
        }

        @Test
        void unaFallaTransitoriaSigueEsperandoElProximoIntento() {
            doThrow(new ReembolsoFallidoException(false)).when(mercadoPago).reembolsar(any(), any());

            assertThat(pagoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);
        }

        @Test
        void siMercadoPagoNoRespondeLaConsultaSeReintentaDespues() {
            when(mercadoPago.consultarPago("mp-1")).thenThrow(new PagoProveedorException("MP caído"));

            assertThat(pagoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);
        }

        @Test
        void pasadas24HorasQuedaParaDevolverAMano() {
            pendiente.setReembolsoSolicitadoAt(AHORA.minusHours(24));
            doThrow(new ReembolsoFallidoException(false)).when(mercadoPago).reembolsar(any(), any());

            assertThat(pagoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.A_REEMBOLSAR);
        }

        @Test
        void siElPagoNoExisteEnMercadoPagoQuedaParaDevolverAMano() {
            when(mercadoPago.consultarPago("mp-1")).thenThrow(new PagoNoEncontradoException("no existe"));

            assertThat(pagoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.A_REEMBOLSAR);
        }

        @Test
        void siOtroProcesoYaLoResolvioNoHaceNada() {
            pendiente.setEstado(EstadoPago.DEVUELTO);

            assertThat(pagoService.reintentarReembolso(1L)).isEqualTo(EstadoPago.DEVUELTO);
            verify(mercadoPago, never()).consultarPago(anyString());
        }

        @Test
        void elWebhookDeDevolucionLoResuelve() {
            pagoService.aplicarResultado(reserva, pendiente, resultado(EstadoPagoExterno.DEVUELTO, "15000", "mp-1", 1L));

            assertThat(pendiente.getEstado()).isEqualTo(EstadoPago.DEVUELTO);
            assertThat(pendiente.getMontoReembolsado()).isEqualByComparingTo("7500");
        }

        @Test
        void unAprobadoRepetidoNoLoVuelveAtras() {
            pagoService.aplicarResultado(reserva, pendiente, resultado(EstadoPagoExterno.APROBADO, "15000", "mp-1", 1L));

            assertThat(pendiente.getEstado()).isEqualTo(EstadoPago.REEMBOLSO_PENDIENTE);
        }
    }

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

            pagoService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL, empleado.getEmail());

            assertThat(publicados()).singleElement().satisfies(a -> {
                assertThat(a.tipo()).isEqualTo(TipoAviso.RESERVA_CONFIRMADA);
                assertThat(a.texto()).contains("Pagó $7.500 de un total de $15.000");
            });
        }

        @Test
        void cobrarElSaldoAvisaSaldoCobrado() {
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            pago(1L, reserva, EstadoPago.APROBADO, "7500");

            pagoService.registrarPagoManual(10L, new BigDecimal("7500"), PaymentProvider.MANUAL, empleado.getEmail());

            assertThat(publicados()).extracting(AvisoReserva::tipo).containsExactly(TipoAviso.SALDO_COBRADO);
        }

        @Test
        void unPagoQueNoLlegaALaSeniaNoAvisa() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            pagoService.registrarPagoManual(10L, new BigDecimal("3000"), PaymentProvider.MANUAL, empleado.getEmail());

            assertThat(publicados()).isEmpty();
        }

        @Test
        void unWebhookRepetidoNoRepiteElAviso() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");

            pagoService.aplicarResultado(reserva, pago, resultado(EstadoPagoExterno.APROBADO, "7500", "mp-1", 1L));
            pagoService.aplicarResultado(reserva, pago, resultado(EstadoPagoExterno.APROBADO, "7500", "mp-1", 1L));

            assertThat(publicados()).extracting(AvisoReserva::tipo).containsExactly(TipoAviso.RESERVA_CONFIRMADA);
        }

        @Test
        void unCobroPorOtroMontoAvisaQueHayQueDevolver() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            Pago pago = pago(1L, reserva, EstadoPago.PENDIENTE, "7500");

            pagoService.aplicarResultado(reserva, pago, resultado(EstadoPagoExterno.APROBADO, "1", "mp-1", 1L));

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

            pagoService.aplicarResultado(reserva, pago, resultado(EstadoPagoExterno.DEVUELTO, "7500", "mp-1", 1L));

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
            when(mercadoPago.consultarPago("mp-1")).thenReturn(resultado(EstadoPagoExterno.APROBADO, "15000", "mp-1", 1L));

            pagoService.reintentarReembolso(1L);

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

            pagoService.aplicarResultado(reserva, pendiente, resultado(EstadoPagoExterno.DEVUELTO, "15000", "mp-1", 1L));

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
            when(mercadoPago.consultarPago("mp-1")).thenReturn(resultado(EstadoPagoExterno.APROBADO, "15000", "mp-1", 1L));
            doThrow(new ReembolsoFallidoException(false)).when(mercadoPago).reembolsar(any(), any());

            pagoService.reintentarReembolso(1L);

            assertThat(publicados()).extracting(AvisoReserva::tipo).containsExactly(TipoAviso.REEMBOLSO_MANUAL);
        }
    }

    @Nested
    class Bonificacion {

        private Usuario admin;

        @BeforeEach
        void unAdmin() {
            admin = usuario(3L, Role.ADMIN);
            when(usuarioService.findByEmailOrThrow(eq(admin.getEmail()), any())).thenReturn(admin);
        }

        @Test
        void sinMontoBonificaTodoYLaReservaQuedaPagaSinPlataCobrada() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            PagoResponse r = pagoService.bonificar(10L, null, "  socio ", admin.getEmail());

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

            pagoService.bonificar(10L, null, null, admin.getEmail());

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);
            assertThat(reserva.getMontoPagado()).isEqualByComparingTo("7500");
            assertThat(reserva.getMontoBonificado()).isEqualByComparingTo("7500");
        }

        @Test
        void unaBonificacionParcialCuentaParaLaSenia() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);

            pagoService.bonificar(10L, new BigDecimal("7500"), null, admin.getEmail());

            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.SEÑADA);
            assertThat(reserva.getMontoPendiente()).isEqualByComparingTo("7500");
        }

        @Test
        void noSeBonificaMasDeLoQueFaltaNiUnaReservaPaga() {
            Reserva reserva = reservaBloqueada(EstadoReserva.SEÑADA);
            pago(1L, reserva, EstadoPago.APROBADO, "7500");

            assertThatThrownBy(() -> pagoService.bonificar(10L, new BigDecimal("8000"), null, admin.getEmail()))
                    .isInstanceOf(ReservaException.class).hasMessageContaining("supera el pendiente");

            pago(2L, reserva, EstadoPago.APROBADO, "7500");
            assertThatThrownBy(() -> pagoService.bonificar(10L, null, null, admin.getEmail()))
                    .isInstanceOf(EstadoInvalidoException.class).hasMessage("La reserva ya está paga.");
        }

        @Test
        void soloElAdminPuedeBonificar() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            assertThatThrownBy(() -> pagoService.bonificar(10L, null, null, empleado.getEmail()))
                    .isInstanceOf(com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException.class)
                    .hasMessage("Solo el ADMIN puede bonificar una reserva.");
            verify(pagoRepository, never()).save(any());
        }

        @Test
        void laBonificacionNoAvisaAlAdmin() {
            reservaBloqueada(EstadoReserva.PENDIENTE);

            pagoService.bonificar(10L, null, null, admin.getEmail());

            verify(eventos, never()).publishEvent(any());
        }

        @Test
        void alCancelarNoSeDevuelveLaBonificacion() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PAGADA);
            Pago bonificacion = pago(1L, reserva, EstadoPago.APROBADO, "7500");
            bonificacion.setProvider(PaymentProvider.BONIFICACION);
            Pago enElClub = pago(2L, reserva, EstadoPago.APROBADO, "7500");
            enElClub.setProvider(PaymentProvider.MANUAL);

            pagoService.reembolsar(reserva, reserva.getMontoPagado());

            assertThat(bonificacion.getEstado()).isEqualTo(EstadoPago.APROBADO);
            assertThat(enElClub.getEstado()).isEqualTo(EstadoPago.A_REEMBOLSAR);
            assertThat(enElClub.getMontoReembolsado()).isEqualByComparingTo("7500");
        }

        @Test
        void crearConBonificacionCreaYBonificaEnLaMismaOperacion() {
            Reserva reserva = reservaBloqueada(EstadoReserva.PENDIENTE);
            ReservaResponse creada = new ReservaResponse();
            creada.setId(10L);
            com.LosCiruelos.padel_club_api.DTOs.Requests.CrearReservaRequest req =
                    new com.LosCiruelos.padel_club_api.DTOs.Requests.CrearReservaRequest();
            com.LosCiruelos.padel_club_api.DTOs.Requests.BonificacionRequest bonif =
                    new com.LosCiruelos.padel_club_api.DTOs.Requests.BonificacionRequest();
            bonif.setMotivo("torneo");
            req.setBonificacion(bonif);
            when(reservaService.crearReserva(req, admin.getEmail())).thenReturn(creada);
            when(reservaService.obtener(10L, admin.getEmail())).thenReturn(creada);

            assertThat(pagoService.crearReservaBonificada(req, admin.getEmail())).isSameAs(creada);
            assertThat(reserva.getEstado()).isEqualTo(EstadoReserva.PAGADA);

            // un empleado no llega a crearla
            assertThatThrownBy(() -> pagoService.crearReservaBonificada(req, empleado.getEmail()))
                    .isInstanceOf(com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException.class);
            verify(reservaService, org.mockito.Mockito.times(1)).crearReserva(any(), anyString());
        }
    }

    @Test
    void emailInexistenteNoLlegaAlServicio() {
        when(usuarioService.findByEmailOrThrow(eq("nadie@test.com"), any()))
                .thenThrow(new ReservaException("Usuario no encontrado"));

        assertThatThrownBy(() -> pagoService.iniciarPago(10L, false, "nadie@test.com"))
                .isInstanceOf(ReservaException.class);
        verify(reservaService, never()).bloquearConAcceso(anyLong(), any());
        verify(mercadoPago, never()).crearPreferencia(any());
        verify(usuarioService, never()).findByEmail(anyString());
    }
}
