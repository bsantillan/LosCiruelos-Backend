package com.LosCiruelos.padel_club_api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.club.controller.ConfiguracionAdminController;
import com.LosCiruelos.padel_club_api.club.controller.ProductoAdminController;
import com.LosCiruelos.padel_club_api.club.controller.CanchaController;
import com.LosCiruelos.padel_club_api.club.controller.ProductoController;
import com.LosCiruelos.padel_club_api.club.dto.response.CanchaResponse;
import com.LosCiruelos.padel_club_api.notificaciones.controller.NotificacionesController;
import com.LosCiruelos.padel_club_api.reservas.dto.response.DisponibilidadResponse;
import com.LosCiruelos.padel_club_api.usuarios.controller.UsuarioAdminController;
import com.LosCiruelos.padel_club_api.usuarios.controller.AuthController;
import com.LosCiruelos.padel_club_api.usuarios.controller.PerfilController;
import com.LosCiruelos.padel_club_api.usuarios.dto.response.LoginResponse;
import com.LosCiruelos.padel_club_api.common.PaginaResponse;
import com.LosCiruelos.padel_club_api.pagos.controller.CobroAdminController;
import com.LosCiruelos.padel_club_api.pagos.controller.MercadoPagoWebhookController;
import com.LosCiruelos.padel_club_api.pagos.controller.PagoController;
import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.usuarios.dto.response.PerfilResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.ProductoResponse;
import com.LosCiruelos.padel_club_api.reservas.service.DisponibilidadService;
import com.LosCiruelos.padel_club_api.reservas.controller.ReservaAdminController;
import com.LosCiruelos.padel_club_api.ventas.controller.VentaAdminController;
import com.LosCiruelos.padel_club_api.ventas.dto.response.VentaResponse;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;
import com.LosCiruelos.padel_club_api.ventas.service.VentaConsultaService;
import com.LosCiruelos.padel_club_api.ventas.service.VentaService;
import com.LosCiruelos.padel_club_api.reservas.controller.ReservaController;
import com.LosCiruelos.padel_club_api.reservas.service.StockService;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.usuarios.dto.response.UsuarioAdminResponse;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.NotificacionesResponse;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.OpcionReembolso;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.TokenType;
import com.LosCiruelos.padel_club_api.reservas.exception.HorarioOcupadoException;
import com.LosCiruelos.padel_club_api.pagos.exception.PagoNoEncontradoException;
import com.LosCiruelos.padel_club_api.pagos.exception.PagoProveedorException;
import com.LosCiruelos.padel_club_api.reservas.exception.ReservaNoEncontradaException;
import com.LosCiruelos.padel_club_api.club.exception.StockInsuficienteException;
import com.LosCiruelos.padel_club_api.common.exception.GlobalExceptionHandler;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioAdminService;
import com.LosCiruelos.padel_club_api.usuarios.service.AuthService;
import com.LosCiruelos.padel_club_api.club.service.CanchaService;
import com.LosCiruelos.padel_club_api.reservas.service.CancelacionService;
import com.LosCiruelos.padel_club_api.notificaciones.service.NotificacionesService;
import com.LosCiruelos.padel_club_api.pagos.service.CobroService;
import com.LosCiruelos.padel_club_api.pagos.service.PagoOnlineService;
import com.LosCiruelos.padel_club_api.usuarios.service.PasswordResetService;
import com.LosCiruelos.padel_club_api.usuarios.service.PerfilService;
import com.LosCiruelos.padel_club_api.club.service.ProductoService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaConsultaService;
import com.LosCiruelos.padel_club_api.usuarios.service.VerificationService;
import com.LosCiruelos.padel_club_api.pagos.mercadopago.MercadoPagoWebhookValidator;
import com.LosCiruelos.padel_club_api.common.Fechas;

import tools.jackson.databind.json.JsonMapper;

/** Controllers con MockMvc standalone: ruteo, validaciones y mapeo de errores a HTTP. */
class ControllersTest {

    private static final String EMAIL = "cliente@test.com";
    private static final Authentication AUTH = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());

    private ReservaService reservaService;
    private CancelacionService cancelacionService;
    private PagoOnlineService pagoOnlineService;
    private CobroService cobroService;
    private com.LosCiruelos.padel_club_api.pagos.service.CobroQrService cobroQrService;
    private com.LosCiruelos.padel_club_api.pagos.service.PagoConsultaService pagoConsultaService;
    private MercadoPagoWebhookValidator webhookValidator;
    private AuthService authService;
    private VerificationService verificationService;
    private PasswordResetService passwordResetService;
    private PerfilService perfilService;
    private UsuarioAdminService usuarioAdminService;
    private CanchaService canchaService;
    private DisponibilidadService disponibilidadService;
    private ProductoService productoService;
    private StockService stockService;
    private NotificacionesService notificacionesService;
    private ConfiguracionService configuracionService;
    private com.LosCiruelos.padel_club_api.club.service.ConfiguracionGeneralService configuracionGeneralService;
    private ReservaConsultaService reservaConsultaService;
    private VentaService ventaService;
    private VentaConsultaService ventaConsultaService;
    private com.LosCiruelos.padel_club_api.ventas.service.VentaQrService ventaQrService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reservaService = mock(ReservaService.class);
        cancelacionService = mock(CancelacionService.class);
        pagoOnlineService = mock(PagoOnlineService.class);
        cobroService = mock(CobroService.class);
        cobroQrService = mock(com.LosCiruelos.padel_club_api.pagos.service.CobroQrService.class);
        pagoConsultaService = mock(com.LosCiruelos.padel_club_api.pagos.service.PagoConsultaService.class);
        webhookValidator = mock(MercadoPagoWebhookValidator.class);
        authService = mock(AuthService.class);
        verificationService = mock(VerificationService.class);
        passwordResetService = mock(PasswordResetService.class);
        perfilService = mock(PerfilService.class);
        usuarioAdminService = mock(UsuarioAdminService.class);
        canchaService = mock(CanchaService.class);
        disponibilidadService = mock(DisponibilidadService.class);
        productoService = mock(ProductoService.class);
        stockService = mock(StockService.class);
        notificacionesService = mock(NotificacionesService.class);
        configuracionService = mock(ConfiguracionService.class);
        configuracionGeneralService = mock(com.LosCiruelos.padel_club_api.club.service.ConfiguracionGeneralService.class);
        reservaConsultaService = mock(ReservaConsultaService.class);
        ventaService = mock(VentaService.class);
        ventaConsultaService = mock(VentaConsultaService.class);
        ventaQrService = mock(com.LosCiruelos.padel_club_api.ventas.service.VentaQrService.class);

        mvc = MockMvcBuilders.standaloneSetup(
                new ReservaController(reservaService, reservaConsultaService, cancelacionService),
                new PagoController(pagoOnlineService),
                new MercadoPagoWebhookController(pagoOnlineService, cobroQrService, webhookValidator),
                new CobroAdminController(cobroService),
                new com.LosCiruelos.padel_club_api.pagos.controller.CobroQrAdminController(cobroQrService),
                new com.LosCiruelos.padel_club_api.pagos.controller.PagoAdminController(pagoConsultaService),
                new com.LosCiruelos.padel_club_api.pagos.controller.MediosDeCobroController(pagoConsultaService),
                new AuthController(authService, verificationService, passwordResetService),
                new PerfilController(perfilService),
                new UsuarioAdminController(usuarioAdminService),
                new CanchaController(canchaService, disponibilidadService),
                new ProductoController(productoService, stockService),
                new ProductoAdminController(productoService, stockService),
                new ConfiguracionAdminController(configuracionService, configuracionGeneralService),
                new com.LosCiruelos.padel_club_api.club.controller.ClubController(configuracionGeneralService),
                new NotificacionesController(notificacionesService),
                new ReservaAdminController(reservaService, reservaConsultaService, cancelacionService, cobroService),
                new VentaAdminController(ventaService, ventaConsultaService, ventaQrService))
                .setControllerAdvice(new GlobalExceptionHandler(TestData.reloj(TestData.AHORA)))
                // mismo formato de fechas que la app
                .setMessageConverters(new JacksonJsonHttpMessageConverter(
                        JsonMapper.builder().addModule(Fechas.moduloJackson()).build()))
                .build();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
        return req.contentType(MediaType.APPLICATION_JSON).content(body).principal(AUTH);
    }

    private static final String CONFIGURACION = """
            {"montoReserva":0,"montoXMediaHora":5000,"porcentajeSenia":50,"duracionMinimaTurno":90,
             "duracionMaximaTurno":180,"minutosReembolsoTotal":1440,"minutosReembolsoSenia":720,
             "recordatorioActivo":true,"minutosRecordatorio":120,"diasAnticipacion":30,
             "horarios":[{"dia":"Lunes","apertura":"08:00","cierre":"23:00"}]}
            """;

    private static final String RESERVA_VALIDA = """
            {"canchaId":1,"fechaReserva":"2099-01-01","horaInicio":"18:00","horaFin":"19:30","productos":[{"productoId":1,"cantidad":2}]}
            """;

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Reservas {

        @Test
        void crearDevuelve201() throws Exception {
            ReservaResponse r = new ReservaResponse();
            r.setId(5L);
            r.setEstado(EstadoReserva.PENDIENTE);
            when(reservaService.crearParaMi(any(), eq(EMAIL))).thenReturn(r);

            mvc.perform(json(post("/reservas"), RESERVA_VALIDA))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(5))
                    .andExpect(jsonPath("$.estado").value("PENDIENTE"));
        }

        @Test
        void camposFaltantesDevuelven400ConElDetalle() throws Exception {
            mvc.perform(json(post("/reservas"), "{\"fechaReserva\":\"2099-01-01\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.canchaId").exists())
                    .andExpect(jsonPath("$.errors.horaInicio").exists());
        }

        @Test
        void turnoOcupadoDevuelve409() throws Exception {
            when(reservaService.crearParaMi(any(), anyString())).thenThrow(new HorarioOcupadoException());

            mvc.perform(json(post("/reservas"), RESERVA_VALIDA))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void elPersonalEligeElReembolsoAlCancelar() throws Exception {
            ReservaResponse cancelada = new ReservaResponse();
            cancelada.setEstado(EstadoReserva.CANCELADA);
            when(cancelacionService.cancelarComoPersonal(4L, OpcionReembolso.SENIA, true, EMAIL)).thenReturn(cancelada);

            mvc.perform(delete("/admin/reservas/4/cancelar").param("reembolso", "SENIA").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CANCELADA"));
            mvc.perform(delete("/admin/reservas/4/cancelar").param("reembolso", "MITAD").principal(AUTH))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void reservaAjenaDevuelve404() throws Exception {
            when(reservaConsultaService.obtener(7L, EMAIL)).thenThrow(new ReservaNoEncontradaException(7L));

            mvc.perform(get("/reservas/7").principal(AUTH)).andExpect(status().isNotFound());
        }

        @Test
        void elPersonalVeElDetalleDeCualquierReserva() throws Exception {
            ReservaResponse r = new ReservaResponse();
            r.setId(7L);
            when(reservaConsultaService.obtener(7L, EMAIL)).thenReturn(r);

            mvc.perform(get("/admin/reservas/7").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(7));
        }

        @Test
        void misReservasCancelarYExpirar() throws Exception {
            when(reservaConsultaService.misReservas(EMAIL, null, null, null, null, false, 0, 10))
                    .thenReturn(new PaginaResponse<>(List.of(new ReservaResponse()), 0, 10, 1, 1));

            mvc.perform(get("/reservas/mias").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido.length()").value(1))
                    .andExpect(jsonPath("$.totalElementos").value(1));
            mvc.perform(delete("/reservas/3/cancelar").principal(AUTH)).andExpect(status().isOk());
            mvc.perform(post("/reservas/3/expirar").principal(AUTH)).andExpect(status().isOk());

            verify(cancelacionService).cancelarComoCliente(3L, EMAIL);
            verify(reservaService).expirarPorUsuario(3L, EMAIL);
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Pagos {

        @Test
        void iniciarYCobrar() throws Exception {
            when(pagoOnlineService.iniciarPago(4L, true, EMAIL)).thenReturn(new PagoResponse());
            when(pagoOnlineService.procesarPago(eq(4L), anyMap(), eq(EMAIL))).thenReturn(new PagoResponse());

            mvc.perform(post("/pagos/4/iniciar").param("pagarTotal", "true").principal(AUTH)).andExpect(status().isOk());
            mvc.perform(json(post("/pagos/4/pago"), "{\"token\":\"tok\",\"payment_method_id\":\"visa\"}"))
                    .andExpect(status().isOk());
        }

        @Test
        void falloDeMercadoPagoDevuelve502() throws Exception {
            when(pagoOnlineService.iniciarPago(4L, false, EMAIL)).thenThrow(new PagoProveedorException("MP caído"));

            mvc.perform(post("/pagos/4/iniciar").principal(AUTH)).andExpect(status().isBadGateway());
        }

        @Test
        void pagoManualValidaElMonto() throws Exception {
            mvc.perform(json(post("/admin/reservas/4/cobros/efectivo"), "{\"monto\":-1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.monto").exists());
            mvc.perform(json(post("/admin/reservas/4/cobros/efectivo"), "{}")).andExpect(status().isBadRequest());

            when(cobroService.registrarPagoManual(any(), any(), any(), any(), anyString())).thenReturn(new PagoResponse());
            mvc.perform(json(post("/admin/reservas/4/cobros/efectivo"), "{\"monto\":7500}")).andExpect(status().isOk());
            mvc.perform(json(post("/admin/reservas/4/cobros/transferencia"), "{\"monto\":7500}")).andExpect(status().isOk());
            // /presencial queda como alias deprecado de la transferencia
            mvc.perform(json(post("/admin/reservas/4/cobros/presencial"), "{\"monto\":7500}")).andExpect(status().isOk());
            verify(cobroService).registrarPagoManual(4L, new BigDecimal("7500"), PaymentProvider.MANUAL, null, EMAIL);
            verify(cobroService, org.mockito.Mockito.times(2)).registrarPagoManual(4L, new BigDecimal("7500"),
                    PaymentProvider.TRANSFERENCIA_MP, null, EMAIL);
        }

        @Test
        void losMediosDeCobroDeUnaReservaConSuEndpoint() throws Exception {
            when(pagoConsultaService.mediosDeCobro()).thenReturn(List.of(
                    new com.LosCiruelos.padel_club_api.pagos.dto.response.MedioDeCobroResponse(
                            com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago.TRANSFERENCIA_MP,
                            "Mercado Pago (transferencia)", true, false, null, "POST",
                            "/admin/reservas/{id}/cobros/transferencia")));

            // no se confunde con GET /admin/reservas/{id}
            mvc.perform(get("/admin/reservas/medios-de-cobro").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].medio").value("TRANSFERENCIA_MP"))
                    .andExpect(jsonPath("$[0].ruta").value("/admin/reservas/{id}/cobros/transferencia"));
        }

        @Test
        void losCobrosAVerificarFiltranPorMedio() throws Exception {
            when(pagoConsultaService.verificables(any(), any(), any(), any())).thenReturn(List.of());

            mvc.perform(get("/admin/pagos/verificables").param("desde", "2026-10-01").param("hasta", "2026-10-31")
                    .param("medio", "TRANSFERENCIA_MP").principal(AUTH))
                    .andExpect(status().isOk());
            verify(pagoConsultaService).verificables(java.time.LocalDate.of(2026, 10, 1),
                    java.time.LocalDate.of(2026, 10, 31), null,
                    List.of(com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago.TRANSFERENCIA_MP));
        }

        @Test
        void cobroConQrGeneraConsultaYCancela() throws Exception {
            var qr = new com.LosCiruelos.padel_club_api.pagos.dto.response.CobroQrResponse(100L, "ORD1",
                    new BigDecimal("15000.00"), TestData.AHORA.plusMinutes(10),
                    com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago.EN_PROCESO, EstadoReserva.PENDIENTE,
                    new BigDecimal("15000.00"));
            when(cobroQrService.generar(any(), any(), any(), anyString())).thenReturn(qr);
            when(cobroQrService.ultimo(4L, EMAIL)).thenReturn(qr);
            when(cobroQrService.cancelar(4L, EMAIL)).thenReturn(qr);

            // sin body: por todo lo que falta
            mvc.perform(post("/admin/reservas/4/cobros/qr").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderId").value("ORD1"))
                    .andExpect(jsonPath("$.qrData").doesNotExist())
                    .andExpect(jsonPath("$.venceAt").value("2026-10-13T10:10:00.000-03:00"));
            verify(cobroQrService).generar(4L, null, null, EMAIL);
            mvc.perform(json(post("/admin/reservas/4/cobros/qr"), "{\"monto\":7500}")).andExpect(status().isOk());
            verify(cobroQrService).generar(4L, new BigDecimal("7500"), null, EMAIL);
            mvc.perform(json(post("/admin/reservas/4/cobros/qr"), "{\"monto\":0}")).andExpect(status().isBadRequest());

            mvc.perform(get("/admin/reservas/4/cobros/qr").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("EN_PROCESO"));
            mvc.perform(delete("/admin/reservas/4/cobros/qr").principal(AUTH)).andExpect(status().isOk());
        }

        @Test
        void sinConfigurarElQrResponde503() throws Exception {
            when(cobroQrService.generar(any(), any(), any(), anyString()))
                    .thenThrow(new com.LosCiruelos.padel_club_api.pagos.exception.CobroQrNoConfiguradoException());

            mvc.perform(post("/admin/reservas/4/cobros/qr").principal(AUTH))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.error").value("El cobro con QR no está configurado."));
        }

        @Test
        void elWebhookDeUnaOrderConsultaLaOrderSinMirarLaFirma() throws Exception {
            mvc.perform(post("/pagos/notificacion").param("type", "order").param("data.id", "ORD1")
                    .header("x-signature", "ts=1,v1=cualquiera").header("x-request-id", "r1"))
                    .andExpect(status().isOk());
            verify(cobroQrService).notificacion("ORD1");

            mvc.perform(post("/pagos/notificacion").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"order\",\"action\":\"order.processed\",\"data\":{\"id\":\"ORD2\"}}"))
                    .andExpect(status().isOk());
            verify(cobroQrService).notificacion("ORD2");
            verify(pagoOnlineService, never()).procesarNotificacion(anyString());
        }

        @Test
        void cobroConCuentaDniYVerificacionDelAdmin() throws Exception {
            mvc.perform(json(post("/admin/reservas/4/cobros/cuenta-dni"), "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.monto").exists());
            when(cobroService.registrarPagoManual(any(), any(), any(), any(), anyString())).thenReturn(new PagoResponse());
            mvc.perform(json(post("/admin/reservas/4/cobros/cuenta-dni"), "{\"monto\":7500}"))
                    .andExpect(status().isOk());
            verify(cobroService).registrarPagoManual(4L, new BigDecimal("7500"), PaymentProvider.CUENTA_DNI, null, EMAIL);

            mvc.perform(json(put("/admin/reservas/4/pagos/9/verificacion"), "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.verificado").exists());
            when(cobroService.verificarPago(4L, 9L, true, EMAIL)).thenReturn(new PagoResponse());
            mvc.perform(json(put("/admin/reservas/4/pagos/9/verificacion"), "{\"verificado\":true}"))
                    .andExpect(status().isOk());
            verify(cobroService).verificarPago(4L, 9L, true, EMAIL);
        }

        @Test
        void listadoDeCuentaDniParaControlar() throws Exception {
            when(pagoConsultaService.cuentaDni(any(), any(), any())).thenReturn(List.of(
                    new com.LosCiruelos.padel_club_api.pagos.dto.response.CobroCuentaDniResponse(
                            com.LosCiruelos.padel_club_api.pagos.dto.response.CobroCuentaDniResponse.Origen.VENTA, 8L,
                            null, com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago.CUENTA_DNI, new BigDecimal("3000.00"), TestData.AHORA, false, null, true)));

            mvc.perform(get("/admin/pagos/cuenta-dni").param("desde", "2026-10-01").param("hasta", "2026-10-31")
                    .param("verificado", "false").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].origen").value("VENTA"))
                    .andExpect(jsonPath("$[0].verificado").value(false))
                    .andExpect(jsonPath("$[0].anulada").value(true));
            verify(pagoConsultaService).cuentaDni(java.time.LocalDate.of(2026, 10, 1),
                    java.time.LocalDate.of(2026, 10, 31), false);
            mvc.perform(get("/admin/pagos/cuenta-dni").param("desde", "2026-10-01").principal(AUTH))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void webhookAceptaLosFormatosDeMercadoPago() throws Exception {
            mvc.perform(post("/pagos/notificacion").param("type", "payment").param("data.id", "111"))
                    .andExpect(status().isOk());
            mvc.perform(post("/pagos/notificacion").param("topic", "payment").param("id", "222"))
                    .andExpect(status().isOk());
            mvc.perform(post("/pagos/notificacion").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"type\":\"payment\",\"data\":{\"id\":\"333\"}}"))
                    .andExpect(status().isOk());

            verify(pagoOnlineService).procesarNotificacion("111");
            verify(pagoOnlineService).procesarNotificacion("222");
            verify(pagoOnlineService).procesarNotificacion("333");
        }

        @Test
        void webhookDeUnPagoInexistenteResponde200() throws Exception {
            // "Simular notificación" del panel de MP manda un id que no existe: no hay que reintentar
            doThrow(new PagoNoEncontradoException("El pago 123456 no existe en Mercado Pago."))
                    .when(pagoOnlineService).procesarNotificacion("123456");

            mvc.perform(post("/pagos/notificacion").param("type", "payment").param("data.id", "123456"))
                    .andExpect(status().isOk());
        }

        @Test
        void lasFechasSalenEnHoraArgentinaConOffsetYMilisegundos() throws Exception {
            ReservaResponse r = new ReservaResponse();
            r.setExpiresAt(java.time.LocalDateTime.of(2099, 1, 1, 18, 15, 0, 123_000_000));
            r.setCreatedAt(java.time.LocalDateTime.of(2099, 1, 1, 18, 0));
            r.setFechaReserva(java.time.LocalDate.of(2099, 1, 2));
            r.setHoraInicio(java.time.LocalTime.of(18, 0));
            when(reservaConsultaService.obtener(9L, EMAIL)).thenReturn(r);

            mvc.perform(get("/reservas/9").principal(AUTH))
                    .andExpect(jsonPath("$.expiresAt").value("2099-01-01T18:15:00.123-03:00"))
                    .andExpect(jsonPath("$.createdAt").value("2099-01-01T18:00:00.000-03:00"))
                    .andExpect(jsonPath("$.fechaReserva").value("2099-01-02"))
                    .andExpect(jsonPath("$.horaInicio").value("18:00"));
        }

        @Test
        void elTimestampDeLosErroresUsaElMismoFormato() throws Exception {
            when(reservaConsultaService.obtener(9L, EMAIL)).thenThrow(new ReservaNoEncontradaException(9L));

            mvc.perform(get("/reservas/9").principal(AUTH))
                    .andExpect(jsonPath("$.timestamp").value("2026-10-13T10:00:00.000-03:00"));
        }

        @Test
        void webhookIgnoraOtrosTopicsYRechazaFirmasInvalidas() throws Exception {
            mvc.perform(post("/pagos/notificacion").param("topic", "merchant_order").param("id", "9"))
                    .andExpect(status().isOk());
            mvc.perform(post("/pagos/notificacion").param("type", "payment"))
                    .andExpect(status().isOk());

            when(webhookValidator.esValida(anyString(), any(), anyString())).thenReturn(false);
            mvc.perform(post("/pagos/notificacion").param("type", "payment").param("data.id", "444")
                    .header("x-signature", "ts=1,v1=mal").header("x-request-id", "r"))
                    .andExpect(status().isUnauthorized());

            verify(pagoOnlineService, never()).procesarNotificacion(anyString());
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Auth {

        @Test
        void registroLoginYGoogle() throws Exception {
            when(authService.login(any())).thenReturn(new LoginResponse());
            when(authService.loginWithGoogle("tok")).thenReturn(new LoginResponse());

            mvc.perform(json(post("/auth/register"), """
                    {"email":"a@b.com","nombre":"Ana","apellido":"Gomez","password":"Clave123!","termsAccepted":true,"aceptarNotificacion":true,
                     "telefono":"1122334455","categoria":"Cuarta","posicion":"Drive"}
                    """)).andExpect(status().isCreated());
            // aceptar las notificaciones es obligatorio
            mvc.perform(json(post("/auth/register"), """
                    {"email":"a@b.com","nombre":"Ana","apellido":"Gomez","password":"Clave123!","termsAccepted":true,"aceptarNotificacion":false,
                     "telefono":"1122334455","categoria":"Cuarta","posicion":"Drive"}
                    """)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.aceptarNotificacion").exists());
            mvc.perform(json(post("/auth/login"), "{\"email\":\"a@b.com\",\"password\":\"x\"}")).andExpect(status().isOk());
            mvc.perform(json(post("/auth/google"), "{\"idToken\":\"tok\"}")).andExpect(status().isOk());
            mvc.perform(json(post("/auth/google"), "{}")).andExpect(status().isBadRequest());
        }

        @Test
        void codigosDeVerificacionYReset() throws Exception {
            mvc.perform(json(post("/auth/verificar-token"),
                    "{\"email\":\"a@b.com\",\"codigo\":\"123456\",\"tipo_codigo\":\"VERIFY_EMAIL\"}")).andExpect(status().isOk());
            mvc.perform(json(post("/auth/verificar-token"),
                    "{\"email\":\"a@b.com\",\"codigo\":\"123456\",\"tipo_codigo\":\"PASSWORD_RESET\"}")).andExpect(status().isOk());
            mvc.perform(json(post("/auth/resend-token"), "{\"email\":\"a@b.com\",\"tipo_codigo\":\"VERIFY_EMAIL\"}"))
                    .andExpect(status().isOk());
            mvc.perform(json(post("/auth/resend-token"), "{\"email\":\"a@b.com\",\"tipo_codigo\":\"PASSWORD_RESET\"}"))
                    .andExpect(status().isOk());
            // antes: NullPointerException → 500
            mvc.perform(json(post("/auth/resend-token"), "{\"email\":\"a@b.com\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.tipo_codigo").exists());
            mvc.perform(json(post("/auth/password-reset/request"), "{\"email\":\"a@b.com\"}")).andExpect(status().isOk());
            mvc.perform(json(put("/auth/password-reset/confirm"), "{\"email\":\"a@b.com\",\"nuevaPassword\":\"Nueva123!\"}"))
                    .andExpect(status().isOk());

            verify(verificationService).verificarToken("a@b.com", "123456");
            verify(passwordResetService).verificarToken("a@b.com", "123456");
            verify(verificationService).enviarToken("a@b.com");
            verify(passwordResetService).resetPassword("a@b.com", "Nueva123!");
        }

        @Test
        void refreshYLogout() throws Exception {
            when(authService.refreshToken("rt")).thenReturn(new LoginResponse());

            mvc.perform(json(post("/auth/refresh"), "{\"refreshToken\":\"rt\"}")).andExpect(status().isOk());
            mvc.perform(json(post("/auth/logout"), "{\"refreshToken\":\"rt\"}")).andExpect(status().isOk());
            mvc.perform(json(post("/auth/logout"), "{}")).andExpect(status().isBadRequest());
            verify(authService).logout("rt");
        }

        @Test
        void tipoDeCodigoInexistenteDevuelve400() throws Exception {
            mvc.perform(json(post("/auth/verificar-token"),
                    "{\"email\":\"a@b.com\",\"codigo\":\"123456\",\"tipo_codigo\":\"OTRO\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class PerfilAdminYCanchas {

        @Test
        void perfil() throws Exception {
            when(perfilService.getPerfil(EMAIL)).thenReturn(new PerfilResponse());
            when(perfilService.updatePerfil(any(), eq(EMAIL))).thenReturn(new PerfilResponse());

            mvc.perform(get("/perfil").principal(AUTH)).andExpect(status().isOk());
            mvc.perform(json(put("/perfil"), "{\"nombre\":\"Ana\",\"apellido\":\"Gomez\",\"telefono\":\"1122334455\"}"))
                    .andExpect(status().isOk());
            // antes no había @Valid
            // el teléfono es opcional: se edita en /perfil/notificaciones
            mvc.perform(json(put("/perfil"), "{\"nombre\":\"Ana\",\"apellido\":\"Gomez\"}"))
                    .andExpect(status().isOk());
            mvc.perform(json(put("/perfil"), "{\"nombre\":\"Ana\",\"apellido\":\"Gomez\",\"telefono\":\"hola\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.telefono").exists());
            mvc.perform(delete("/perfil/desactivar").principal(AUTH)).andExpect(status().isOk());
            verify(perfilService).desactivarPerfil(EMAIL);
        }

        @Test
        void administracionDeUsuarios() throws Exception {
            when(usuarioAdminService.crearUsuario(any())).thenReturn(new UsuarioAdminResponse());
            when(usuarioAdminService.editarUsuario(eq(2L), any())).thenReturn(new UsuarioAdminResponse());
            when(usuarioAdminService.listarUsuarios(any())).thenReturn(List.of());
            when(usuarioAdminService.getUsuario(2L, EMAIL)).thenReturn(new UsuarioAdminResponse());
            when(usuarioAdminService.reactivarUsuario(2L)).thenReturn(new UsuarioAdminResponse());
            when(usuarioAdminService.desactivarUsuario(2L)).thenReturn(new UsuarioAdminResponse());
            String body = "{\"email\":\"e@b.com\",\"nombre\":\"Emp\",\"apellido\":\"Leado\",\"telefono\":\"1122334455\",\"rol\":\"EMPLEADO\"}";

            mvc.perform(json(post("/admin/usuarios"), body)).andExpect(status().isCreated());
            mvc.perform(json(put("/admin/usuarios/2"), body)).andExpect(status().isOk());
            mvc.perform(get("/admin/usuarios").param("rol", "CLIENTE")).andExpect(status().isOk());
            mvc.perform(get("/admin/usuarios/2").principal(AUTH)).andExpect(status().isOk());
            mvc.perform(put("/admin/usuarios/2/reactivar")).andExpect(status().isOk());
            mvc.perform(delete("/admin/usuarios/2/desactivar")).andExpect(status().isOk());
        }

        @Test
        void canchasYDisponibilidad() throws Exception {
            when(canchaService.listarCanchas()).thenReturn(List.of(new CanchaResponse()));
            when(disponibilidadService.disponibilidad(any(), any(), any())).thenReturn(new DisponibilidadResponse());

            mvc.perform(get("/canchas").principal(AUTH)).andExpect(status().isOk());
            mvc.perform(get("/canchas/1/disponibilidad").param("fecha", "2099-01-01").param("duracion", "90"))
                    .andExpect(status().isOk());
            // duracion es opcional (el servicio usa la mínima configurada)
            mvc.perform(get("/canchas/1/disponibilidad").param("fecha", "2099-01-01"))
                    .andExpect(status().isOk());
            verify(disponibilidadService).disponibilidad(1L, java.time.LocalDate.of(2099, 1, 1), null);
            // antes: 500
            mvc.perform(get("/canchas/1/disponibilidad")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.fecha").exists());
            mvc.perform(get("/canchas/1/disponibilidad").param("fecha", "mañana")).andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Productos {

        @Test
        void catalogoYDisponibilidad() throws Exception {
            when(productoService.listarParaReservar()).thenReturn(List.of(new ProductoResponse()));
            when(stockService.disponibilidad(any(), any(), any())).thenReturn(List.of());

            mvc.perform(get("/productos").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(get("/productos/disponibilidad").param("fecha", "2099-01-01")
                    .param("horaInicio", "18:00").param("horaFin", "19:30"))
                    .andExpect(status().isOk());
            mvc.perform(get("/productos/disponibilidad").param("fecha", "2099-01-01"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void disponibilidadDeTodosLosProductosParaElPersonal() throws Exception {
            when(stockService.disponibilidadParaPersonal(any(), any(), any(), any())).thenReturn(List.of(
                    new com.LosCiruelos.padel_club_api.reservas.dto.response.ProductoDisponibleResponse(6L, null, "Paleta premium",
                            com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto.ALQUILER,
                            new BigDecimal("4000"), 1, false)));

            mvc.perform(get("/admin/configuracion/productos/disponibilidad").param("fecha", "2099-01-01")
                    .param("horaInicio", "18:00").param("horaFin", "19:30").param("excluirReservaId", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].disponibles").value(1))
                    .andExpect(jsonPath("$[0].ofrecerAlReservar").value(false));
            verify(stockService).disponibilidadParaPersonal(java.time.LocalDate.of(2099, 1, 1), java.time.LocalTime.of(18, 0),
                    java.time.LocalTime.of(19, 30), 50L);
            mvc.perform(get("/admin/configuracion/productos/disponibilidad").param("fecha", "2099-01-01")
                    .param("horaInicio", "18:00").param("horaFin", "19:30"))
                    .andExpect(status().isOk());
            verify(stockService).disponibilidadParaPersonal(java.time.LocalDate.of(2099, 1, 1), java.time.LocalTime.of(18, 0),
                    java.time.LocalTime.of(19, 30), null);

            // los mismos errores que GET /productos/disponibilidad
            mvc.perform(get("/admin/configuracion/productos/disponibilidad").param("fecha", "2099-01-01"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/productos/disponibilidad").param("fecha", "2099-01-01"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/admin/configuracion/productos/disponibilidad").param("fecha", "01/01/2099")
                    .param("horaInicio", "18:00").param("horaFin", "19:30"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/admin/configuracion/productos/disponibilidad").param("fecha", "2099-01-01")
                    .param("horaInicio", "18hs").param("horaFin", "19:30"))
                    .andExpect(status().isBadRequest());
            when(stockService.disponibilidadParaPersonal(any(), eq(java.time.LocalTime.of(19, 30)), any(), any()))
                    .thenThrow(new com.LosCiruelos.padel_club_api.common.exception.ReservaException(
                            "La hora de inicio debe ser anterior a la hora de fin."));
            mvc.perform(get("/admin/configuracion/productos/disponibilidad").param("fecha", "2099-01-01")
                    .param("horaInicio", "19:30").param("horaFin", "18:00"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("La hora de inicio debe ser anterior a la hora de fin."));
        }

        @Test
        void unHorarioQueDejaReservasAfueraEs409ConLaLista() throws Exception {
            var fuera = new com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse(
                    List.of(new com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse.Reserva(31L,
                            java.time.LocalDate.of(2026, 10, 19), java.time.LocalTime.of(8, 0),
                            java.time.LocalTime.of(9, 30), 2, "Juan Gómez", "NORMAL")),
                    List.of(new com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse.TurnoFijo(4L,
                            java.time.DayOfWeek.MONDAY, java.time.LocalTime.of(8, 0), java.time.LocalTime.of(9, 30), 1)));
            when(configuracionGeneralService.actualizar(any(), any())).thenThrow(
                    com.LosCiruelos.padel_club_api.club.exception.HorariosConReservasException.porHorario(fuera));

            mvc.perform(json(put("/admin/configuracion"), CONFIGURACION))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("El horario nuevo deja afuera 1 reserva y 1 turno fijo. "
                            + "Para guardarlo igual, mandá \"confirmar\": true: las reservas se cancelan con reembolso "
                            + "total y se avisa a cada cliente, y los turnos fijos no crean partidos nuevos en ese horario."))
                    .andExpect(jsonPath("$.reservasFueraDeHorario[0].id").value(31))
                    .andExpect(jsonPath("$.reservasFueraDeHorario[0].fecha").value("2026-10-19"))
                    .andExpect(jsonPath("$.reservasFueraDeHorario[0].horaInicio").value("08:00"))
                    .andExpect(jsonPath("$.reservasFueraDeHorario[0].canchaNumero").value(2))
                    .andExpect(jsonPath("$.reservasFueraDeHorario[0].clienteNombre").value("Juan Gómez"))
                    .andExpect(jsonPath("$.reservasFueraDeHorario[0].tipo").value("NORMAL"))
                    .andExpect(jsonPath("$.turnosFijosFueraDeHorario[0].diaSemana").value("MONDAY"))
                    .andExpect(jsonPath("$.turnosFijosFueraDeHorario[0].horaFin").value("09:30"));

            // confirmar llega al service
            mvc.perform(json(put("/admin/configuracion"), CONFIGURACION.replace("\"diasAnticipacion\":30",
                    "\"diasAnticipacion\":30,\"confirmar\":true")));
            org.mockito.ArgumentCaptor<com.LosCiruelos.padel_club_api.club.dto.request.ConfiguracionRequest> req =
                    org.mockito.ArgumentCaptor.forClass(com.LosCiruelos.padel_club_api.club.dto.request.ConfiguracionRequest.class);
            verify(configuracionGeneralService, org.mockito.Mockito.times(2)).actualizar(req.capture(), eq(EMAIL));
            org.assertj.core.api.Assertions.assertThat(req.getAllValues())
                    .extracting(com.LosCiruelos.padel_club_api.club.dto.request.ConfiguracionRequest::getConfirmar)
                    .containsExactly(null, true);
        }

        @Test
        void losHorariosDelClub() throws Exception {
            when(configuracionGeneralService.horarios()).thenReturn(
                    new com.LosCiruelos.padel_club_api.club.dto.response.HorariosClubResponse(List.of(
                            new com.LosCiruelos.padel_club_api.club.dto.response.ConfiguracionResponse.Horario("Lunes",
                                    java.time.LocalTime.of(8, 0), java.time.LocalTime.of(23, 0))),
                            List.of(new com.LosCiruelos.padel_club_api.club.dto.response.HorariosClubResponse.Cierre(
                                    java.time.LocalDate.of(2026, 12, 25), "Navidad")), 90, 180));

            mvc.perform(get("/club/horarios"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.horarios[0].dia").value("Lunes"))
                    .andExpect(jsonPath("$.horarios[0].apertura").value("08:00"))
                    .andExpect(jsonPath("$.horarios[0].cierre").value("23:00"))
                    .andExpect(jsonPath("$.duracionMinimaTurno").value(90))
                    .andExpect(jsonPath("$.duracionMaximaTurno").value(180))
                    .andExpect(jsonPath("$.cierres[0].fecha").value("2026-12-25"))
                    .andExpect(jsonPath("$.cierres[0].motivo").value("Navidad"));
        }

        @Test
        void losDiasCerrados() throws Exception {
            var cancelaciones = new com.LosCiruelos.padel_club_api.club.dto.response.CancelacionesResponse(List.of(31L),
                    List.of(new com.LosCiruelos.padel_club_api.club.dto.response.CancelacionesResponse.SinCancelar(32L,
                            "La reserva tiene un pago en proceso; esperá a que Mercado Pago lo resuelva.")));
            when(configuracionGeneralService.cerrar(any(), eq(EMAIL))).thenReturn(
                    new com.LosCiruelos.padel_club_api.club.dto.response.CierreResponse(java.time.LocalDate.of(2026, 12, 25),
                            "Navidad", TestData.AHORA, cancelaciones));

            mvc.perform(json(post("/admin/configuracion/cierres"), "{\"motivo\":\" \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.fecha").exists())
                    .andExpect(jsonPath("$.errors.motivo").exists());
            mvc.perform(json(post("/admin/configuracion/cierres"),
                    "{\"fecha\":\"2026-12-25\",\"motivo\":\"Navidad\",\"confirmar\":true}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.fecha").value("2026-12-25"))
                    .andExpect(jsonPath("$.cancelaciones.canceladas[0]").value(31))
                    .andExpect(jsonPath("$.cancelaciones.sinCancelar[0].reservaId").value(32));
            org.mockito.ArgumentCaptor<com.LosCiruelos.padel_club_api.club.dto.request.CierreRequest> req =
                    org.mockito.ArgumentCaptor.forClass(com.LosCiruelos.padel_club_api.club.dto.request.CierreRequest.class);
            verify(configuracionGeneralService).cerrar(req.capture(), eq(EMAIL));
            org.assertj.core.api.Assertions.assertThat(req.getValue().getConfirmar()).isTrue();

            when(configuracionGeneralService.cierres()).thenReturn(List.of(
                    new com.LosCiruelos.padel_club_api.club.dto.response.CierreResponse(java.time.LocalDate.of(2026, 12, 25),
                            "Navidad", TestData.AHORA, null)));
            mvc.perform(get("/admin/configuracion/cierres").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].motivo").value("Navidad"));

            mvc.perform(delete("/admin/configuracion/cierres/2026-12-25").principal(AUTH))
                    .andExpect(status().isNoContent());
            verify(configuracionGeneralService).reabrir(java.time.LocalDate.of(2026, 12, 25));
        }

        @Test
        void seccionStockDeLaConfiguracion() throws Exception {
            when(productoService.listarTodos()).thenReturn(List.of());
            when(productoService.crear(any())).thenReturn(new ProductoResponse());
            when(productoService.actualizar(eq(1L), any())).thenReturn(new ProductoResponse());
            when(productoService.ajustarStock(1L, 12)).thenReturn(new ProductoResponse());
            when(productoService.desactivar(1L)).thenReturn(new ProductoResponse());
            String producto = "{\"nombre\":\"Agua\",\"tipo\":\"VENTA\",\"precio\":800,\"stock\":24}";

            mvc.perform(get("/admin/configuracion/productos")).andExpect(status().isOk());
            mvc.perform(json(post("/admin/configuracion/productos"), producto)).andExpect(status().isCreated());
            mvc.perform(json(put("/admin/configuracion/productos/1"), producto)).andExpect(status().isOk());
            mvc.perform(json(patch("/admin/configuracion/productos/1/stock"), "{\"ajuste\":12}"))
                    .andExpect(status().isOk());
            // un producto no se borra: se da de baja y se reactiva
            when(productoService.reactivar(1L)).thenReturn(new ProductoResponse());
            mvc.perform(put("/admin/configuracion/productos/1/baja")).andExpect(status().isOk());
            mvc.perform(put("/admin/configuracion/productos/1/reactivacion")).andExpect(status().isOk());
            verify(productoService).desactivar(1L);
            verify(productoService).reactivar(1L);
            mvc.perform(delete("/admin/configuracion/productos/1")).andExpect(status().isMethodNotAllowed());
        }

        @Test
        void validaLosDatosDelProducto() throws Exception {
            mvc.perform(json(post("/admin/configuracion/productos"),
                    "{\"nombre\":\"\",\"tipo\":\"VENTA\",\"precio\":-1,\"stock\":-5}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.nombre").exists())
                    .andExpect(jsonPath("$.errors.precio").exists())
                    .andExpect(jsonPath("$.errors.stock").exists());
            mvc.perform(json(patch("/admin/configuracion/productos/1/stock"), "{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void devolucionDePaletas() throws Exception {
            when(reservaConsultaService.devolucionesPendientes(EMAIL)).thenReturn(List.of(new ReservaResponse()));
            when(reservaService.registrarDevolucion(5L, 2, EMAIL)).thenReturn(new ReservaResponse());

            mvc.perform(get("/admin/reservas/devoluciones-pendientes").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(json(post("/admin/reservas/5/devolucion"), "{\"paletasDevueltas\":2}")).andExpect(status().isOk());
            mvc.perform(json(post("/admin/reservas/5/devolucion"), "{\"paletasDevueltas\":-1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.paletasDevueltas").exists());
            verify(reservaService).registrarDevolucion(5L, 2, EMAIL);
        }

        @Test
        void saldosPendientes() throws Exception {
            when(reservaConsultaService.saldosPendientes(EMAIL)).thenReturn(List.of(new ReservaResponse(), new ReservaResponse()));

            mvc.perform(get("/admin/reservas/saldos-pendientes").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void stockInsuficienteDevuelve409() throws Exception {
            when(reservaService.crearParaMi(any(), anyString()))
                    .thenThrow(new StockInsuficienteException("Solo quedan 2 paletas disponibles para ese horario."));

            mvc.perform(json(post("/reservas"), RESERVA_VALIDA))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("Solo quedan 2 paletas disponibles para ese horario."));
        }

        @Test
        void cantidadesInvalidasEnLaReserva() throws Exception {
            mvc.perform(json(post("/reservas"), """
                    {"canchaId":1,"fechaReserva":"2099-01-01","horaInicio":"18:00","horaFin":"19:30",
                     "productos":[{"productoId":1,"cantidad":0},{"cantidad":2}]}
                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors['productos[0].cantidad']").value("La cantidad tiene que ser al menos 1"))
                    .andExpect(jsonPath("$.errors['productos[1].productoId']").value("Indicá el producto (productoId)"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class MisReservasConFiltros {

        @Test
        void pasaLosFiltrosAlServicio() throws Exception {
            when(reservaConsultaService.misReservas(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                    .thenReturn(new PaginaResponse<>(List.of(), 1, 5, 0, 0));

            mvc.perform(get("/reservas/mias").principal(AUTH)
                    .param("tipo", "historial").param("estado", "SEÑADA,PAGADA")
                    .param("desde", "2026-10-01").param("hasta", "2026-10-31")
                    .param("page", "1").param("size", "5"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pagina").value(1));

            verify(reservaConsultaService).misReservas(EMAIL, "historial",
                    List.of(EstadoReserva.SEÑADA, EstadoReserva.PAGADA),
                    java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 31), false, 1, 5);
        }

        @Test
        void pideLasDescartadas() throws Exception {
            when(reservaConsultaService.misReservas(any(), any(), any(), any(), any(), anyBoolean(), anyInt(), anyInt()))
                    .thenReturn(new PaginaResponse<>(List.of(), 0, 10, 0, 0));

            mvc.perform(get("/reservas/mias").principal(AUTH).param("incluirDescartadas", "true"))
                    .andExpect(status().isOk());

            verify(reservaConsultaService).misReservas(EMAIL, null, null, null, null, true, 0, 10);
        }

        @Test
        void estadoOFechaInvalidosDevuelven400() throws Exception {
            mvc.perform(get("/reservas/mias").principal(AUTH).param("estado", "JUGADA"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/reservas/mias").principal(AUTH).param("desde", "ayer"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Errores {

        @Test
        void jsonMalFormadoDevuelve400() throws Exception {
            mvc.perform(json(post("/reservas"), "{esto no es json")).andExpect(status().isBadRequest());
        }

        @Test
        void sinPermisosDevuelve403() throws Exception {
            when(reservaConsultaService.misReservas(EMAIL, null, null, null, null, false, 0, 10)).thenThrow(new AccessDeniedException("no"));
            mvc.perform(get("/reservas/mias").principal(AUTH)).andExpect(status().isForbidden());
        }

        @Test
        void conflictoDeIntegridadDevuelve409() throws Exception {
            when(reservaConsultaService.misReservas(EMAIL, null, null, null, null, false, 0, 10)).thenThrow(new DataIntegrityViolationException("dup"));
            mvc.perform(get("/reservas/mias").principal(AUTH)).andExpect(status().isConflict());
        }

        @Test
        void errorInesperadoDevuelve500SinDetallesInternos() throws Exception {
            when(reservaConsultaService.misReservas(EMAIL, null, null, null, null, false, 0, 10)).thenThrow(new IllegalStateException("password=secreta"));
            mvc.perform(get("/reservas/mias").principal(AUTH))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.error").value("Ocurrió un error inesperado. Intentá de nuevo."));
        }

        @Test
        void metodoNoPermitido() throws Exception {
            mvc.perform(put("/reservas/mias").principal(AUTH)).andExpect(status().isMethodNotAllowed());
        }

        @Test
        void enumInvalidoEnElCodigoDeVerificacion() throws Exception {
            mvc.perform(json(post("/auth/resend-token"), "{\"email\":\"a@b.com\",\"tipo_codigo\":\"" + TokenType.class + "\"}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Notificaciones {

        private final NotificacionesResponse respuesta = new NotificacionesResponse(CanalNotificacion.WHATSAPP,
                CanalNotificacion.EMAIL, "5491122334455", false, true);

        @Test
        void elegirElCanal() throws Exception {
            when(notificacionesService.actualizar(EMAIL, CanalNotificacion.WHATSAPP, "11 2233-4455"))
                    .thenReturn(respuesta);

            mvc.perform(json(put("/perfil/notificaciones"), "{\"canal\":\"WHATSAPP\",\"telefono\":\"11 2233-4455\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.canal").value("WHATSAPP"))
                    .andExpect(jsonPath("$.canalEnUso").value("EMAIL"))
                    .andExpect(jsonPath("$.telefonoVerificado").value(false));

            mvc.perform(json(put("/perfil/notificaciones"), "{\"canal\":\"SMS\"}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(json(put("/perfil/notificaciones"), "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.canal").exists());
        }

        @Test
        void verificarElTelefono() throws Exception {
            when(notificacionesService.verificarTelefono(EMAIL, "123456")).thenReturn(respuesta);

            mvc.perform(json(post("/perfil/telefono/verificar"), "{\"codigo\":\"123456\"}"))
                    .andExpect(status().isOk());
            mvc.perform(json(post("/perfil/telefono/verificar"), "{\"codigo\":\"12ab\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.codigo").exists());
            mvc.perform(json(post("/perfil/telefono/codigo"), "")).andExpect(status().isOk());
            verify(notificacionesService).enviarCodigo(EMAIL);
        }

        @Test
        void avisosDelAdminYRecordatorio() throws Exception {
            mvc.perform(json(put("/perfil/avisos"), "{\"reservaConfirmada\":true}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(json(put("/admin/configuracion"), "{\"horarios\":[{\"dia\":\"Lunes\"}]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.montoXMediaHora").value("Es obligatorio"))
                    .andExpect(jsonPath("$.errors.diasAnticipacion").exists())
                    .andExpect(jsonPath("$.errors['horarios[0].apertura']").value("Indicá la hora de apertura"));
            mvc.perform(json(put("/admin/configuracion"), CONFIGURACION.replace("\"porcentajeSenia\":50", "\"porcentajeSenia\":150")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.porcentajeSenia").value("Entre 0 y 100"));
            verify(configuracionGeneralService, never()).actualizar(any(), any());
            com.LosCiruelos.padel_club_api.club.dto.response.ConfiguracionResponse respuesta =
                    new com.LosCiruelos.padel_club_api.club.dto.response.ConfiguracionResponse(new BigDecimal("0.00"),
                            new BigDecimal("5000.00"), new BigDecimal("50.00"), 90, 180, 1440, 720, true, 120, 30, 120,
                            List.of(new com.LosCiruelos.padel_club_api.club.dto.response.ConfiguracionResponse.Horario(
                                    "Lunes", java.time.LocalTime.of(8, 0), java.time.LocalTime.of(23, 0))), null);
            when(configuracionGeneralService.actualizar(any(), any())).thenReturn(respuesta);
            when(configuracionGeneralService.general()).thenReturn(respuesta);
            mvc.perform(json(put("/admin/configuracion"), CONFIGURACION))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.horarios[0].apertura").value("08:00"));
            org.mockito.ArgumentCaptor<com.LosCiruelos.padel_club_api.club.dto.request.ConfiguracionRequest> req =
                    org.mockito.ArgumentCaptor.forClass(com.LosCiruelos.padel_club_api.club.dto.request.ConfiguracionRequest.class);
            verify(configuracionGeneralService).actualizar(req.capture(), eq(EMAIL));
            org.assertj.core.api.Assertions.assertThat(req.getValue().getHorarios().get(0).getCierre())
                    .isEqualTo(java.time.LocalTime.of(23, 0));
            mvc.perform(get("/admin/configuracion").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.montoXMediaHora").value(5000.00))
                    .andExpect(jsonPath("$.horarios[0].dia").value("Lunes"));

            mvc.perform(json(put("/admin/configuracion/avisos"), "{\"recordatorioActivo\":true,\"minutosRecordatorio\":5}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.minutosRecordatorio").exists());
            mvc.perform(json(put("/admin/configuracion/avisos"), "{\"recordatorioActivo\":true,\"minutosRecordatorio\":120}"))
                    .andExpect(status().isOk());
        }

        @Test
        void anticipacionParaReservar() throws Exception {
            mvc.perform(json(put("/admin/configuracion/reservas"), "{\"diasAnticipacion\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.diasAnticipacion").value("Mínimo 1 día"));
            mvc.perform(json(put("/admin/configuracion/reservas"), "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.diasAnticipacion").value("Es obligatorio"));
            mvc.perform(json(put("/admin/configuracion/reservas"), "{\"diasAnticipacion\":30}"))
                    .andExpect(status().isOk());
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Resumen {

        @Test
        void devuelveElResumenConMontosYFechasEnElFormatoDeSiempre() throws Exception {
            java.time.LocalDate desde = java.time.LocalDate.of(2026, 10, 1);
            java.time.LocalDate hasta = java.time.LocalDate.of(2026, 10, 31);
            when(reservaConsultaService.resumen(desde, hasta, "admin@test.com")).thenReturn(new com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse(
                    desde, hasta,
                    new com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.Jugados(28,
                            new BigDecimal("420000.00"), new BigDecimal("380000.00"), new BigDecimal("15000.00"),
                            new BigDecimal("25000.00")),
                    new com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.PorJugar(6,
                            new BigDecimal("90000.00"), new BigDecimal("45000.00"), new BigDecimal("0.00"),
                            new BigDecimal("45000.00")),
                    new com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.Caja(
                            new BigDecimal("230000.00"), new BigDecimal("90000.00"), new BigDecimal("100000.00"),
                            new BigDecimal("40000.00"), new BigDecimal("7500.00"), new BigDecimal("222500.00"), new com.LosCiruelos.padel_club_api.pagos.dto.response.DetalleMercadoPago(new BigDecimal("100000.00"), new BigDecimal("20000.00"), new BigDecimal("70000.00"), new BigDecimal("10000.00")), new BigDecimal("5000.00"),
                            java.time.LocalDate.of(2026, 10, 1).atTime(8, 0), java.time.LocalDate.of(2026, 11, 2).atTime(8, 0)),
                    new com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.Cancelaciones(3,
                            new BigDecimal("7500.00")),
                    new com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.Ventas(3,
                            new BigDecimal("15000.00"), new BigDecimal("1500.00")),
                    List.of(new com.LosCiruelos.padel_club_api.reservas.repository.ProductoVendido(1L, "Pelotas",
                            com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto.VENTA, 40L,
                            new BigDecimal("200000.00")))));

            mvc.perform(get("/admin/reservas/resumen").principal(ADMIN).param("desde", "2026-10-01").param("hasta", "2026-10-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.desde").value("2026-10-01"))
                    .andExpect(jsonPath("$.hasta").value("2026-10-31"))
                    .andExpect(jsonPath("$.jugados.turnos").value(28))
                    .andExpect(jsonPath("$.ventas.cantidad").value(3))
                    .andExpect(jsonPath("$.ventas.total").value(15000.00))
                    .andExpect(jsonPath("$.ventas.devoluciones").value(1500.00))
                    .andExpect(jsonPath("$.jugados.montoTotal").value(420000.00))
                    .andExpect(jsonPath("$.jugados.montoCobrado").value(380000.00))
                    .andExpect(jsonPath("$.jugados.montoBonificado").value(15000.00))
                    .andExpect(jsonPath("$.jugados.deuda").value(25000.00))
                    .andExpect(jsonPath("$.porJugar.turnos").value(6))
                    .andExpect(jsonPath("$.porJugar.cobradoAnticipado").value(45000.00))
                    .andExpect(jsonPath("$.porJugar.aCobrar").value(45000.00))
                    .andExpect(jsonPath("$.caja.total").value(230000.00))
                    .andExpect(jsonPath("$.caja.bonificado").value(5000.00))
                    .andExpect(jsonPath("$.caja.inicio").value("2026-10-01T08:00:00.000-03:00"))
                    .andExpect(jsonPath("$.caja.fin").value("2026-11-02T08:00:00.000-03:00"))
                    .andExpect(jsonPath("$.caja.efectivo").value(90000.00))
                    .andExpect(jsonPath("$.caja.mercadoPago").value(100000.00))
                    .andExpect(jsonPath("$.caja.cuentaDni").value(40000.00))
                    .andExpect(jsonPath("$.caja.mercadoPagoOnline").doesNotExist())
                    .andExpect(jsonPath("$.caja.devoluciones").value(7500.00))
                    .andExpect(jsonPath("$.caja.neto").value(222500.00))
                    .andExpect(jsonPath("$.cancelaciones.turnos").value(3))
                    .andExpect(jsonPath("$.cancelaciones.retenido").value(7500.00))
                    .andExpect(jsonPath("$.montoEsperado").doesNotExist())
                    .andExpect(jsonPath("$.productos[0].nombre").value("Pelotas"))
                    .andExpect(jsonPath("$.productos[0].tipo").value("VENTA"))
                    .andExpect(jsonPath("$.productos[0].cantidad").value(40));
        }

        private final Authentication ADMIN = new UsernamePasswordAuthenticationToken("admin@test.com", null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")));
        private final Authentication EMPLEADO = new UsernamePasswordAuthenticationToken("emp@test.com", null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_EMPLEADO")));

        @Test
        void pasaQuienPideElResumenParaQueElServiceDecidaElPeriodo() throws Exception {
            mvc.perform(get("/admin/reservas/resumen").principal(EMPLEADO)).andExpect(status().isOk());
            verify(reservaConsultaService).resumen(null, null, "emp@test.com");

            mvc.perform(get("/admin/reservas/resumen").principal(ADMIN)).andExpect(status().isOk());
            verify(reservaConsultaService).resumen(null, null, "admin@test.com");

            when(reservaConsultaService.resumen(any(), any(), eq("emp@test.com")))
                    .thenThrow(new com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException(
                            "Solo podés ver el resumen de hoy."));
            mvc.perform(get("/admin/reservas/resumen").principal(EMPLEADO).param("desde", "2026-10-01").param("hasta", "2026-10-31"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Solo podés ver el resumen de hoy."));
        }

        @Test
        void unaFechaConFormatoInvalidoDevuelve400() throws Exception {
            mvc.perform(get("/admin/reservas/resumen").principal(ADMIN).param("desde", "01/10/2026").param("hasta", "2026-10-31"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.desde").exists());
        }

        @Test
        void unPeriodoInvalidoDevuelve400() throws Exception {
            when(reservaConsultaService.resumen(any(), any(), eq("admin@test.com")))
                    .thenThrow(new com.LosCiruelos.padel_club_api.common.exception.ReservaException(
                            "La fecha desde no puede ser posterior a hasta."));

            mvc.perform(get("/admin/reservas/resumen").principal(ADMIN).param("desde", "2026-10-31").param("hasta", "2026-10-01"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("La fecha desde no puede ser posterior a hasta."));
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class ReservasYClientesDelPersonal {

        private com.LosCiruelos.padel_club_api.reservas.dto.response.ListadoReservasResponse pagina() {
            ReservaResponse r = new ReservaResponse();
            r.setId(10L);
            r.setEstado(EstadoReserva.SEÑADA);
            return new com.LosCiruelos.padel_club_api.reservas.dto.response.ListadoReservasResponse(List.of(r), 0, 20, 57, 3,
                    new com.LosCiruelos.padel_club_api.reservas.dto.response.ListadoReservasResponse.Totales(57,
                            new BigDecimal("855000.00"), new BigDecimal("690000.00"), new BigDecimal("410000.00"),
                            new BigDecimal("250000.00"), new BigDecimal("30000.00"), new BigDecimal("15000.00"),
                            new BigDecimal("165000.00"), null));
        }

        @Test
        void listaConTodosLosFiltrosYLosTotales() throws Exception {
            when(reservaConsultaService.listar(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyInt(), any(),
                    any(), any())).thenReturn(pagina());

            mvc.perform(get("/admin/reservas").principal(AUTH)
                    .param("desde", "2026-10-01").param("hasta", "2026-10-31")
                    .param("estado", "SEÑADA,PARCIALMENTE_PAGADA").param("clienteId", "7").param("cliente", "pérez")
                    .param("canchaId", "2").param("pendientes", "true").param("page", "1").param("size", "50")
                    .param("orden", "desc"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.contenido[0].id").value(10))
                    .andExpect(jsonPath("$.totalElementos").value(57))
                    .andExpect(jsonPath("$.totales.reservas").value(57))
                    .andExpect(jsonPath("$.totales.cobradoEfectivo").value(410000.00))
                    .andExpect(jsonPath("$.totales.cobradoMercadoPago").value(250000.00))
                    .andExpect(jsonPath("$.totales.cobradoCuentaDni").value(30000.00))
                    .andExpect(jsonPath("$.totales.montoBonificado").value(15000.00))
                    .andExpect(jsonPath("$.totales.montoPendiente").value(165000.00));

            verify(reservaConsultaService).listar(java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 31),
                    List.of(EstadoReserva.SEÑADA, EstadoReserva.PARCIALMENTE_PAGADA), 7L, "pérez", 2L, null, null, null, true, 1, 50,
                    "desc", EMAIL);
        }

        @Test
        void filtraPorTurnoFijo() throws Exception {
            when(reservaConsultaService.listar(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(),
                    anyInt(), any(), any(), any())).thenReturn(pagina());

            mvc.perform(get("/admin/reservas").principal(AUTH).param("turnoFijoId", "8").param("deTurnoFijo", "true"))
                    .andExpect(status().isOk());
            verify(reservaConsultaService).listar(null, null, null, null, null, null, null, 8L, true, false, 0, null, null, EMAIL);

            mvc.perform(get("/admin/reservas").principal(AUTH).param("deTurnoFijo", "talvez"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void sinParametrosUsaLosValoresPorDefecto() throws Exception {
            when(reservaConsultaService.listar(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyInt(), any(),
                    any(), any())).thenReturn(pagina());

            mvc.perform(get("/admin/reservas").principal(AUTH)).andExpect(status().isOk());

            verify(reservaConsultaService).listar(null, null, null, null, null, null, null, null, null, false, 0, null, null, EMAIL);
        }

        @Test
        void unEstadoInexistenteOUnaFechaInvalidaDevuelven400() throws Exception {
            mvc.perform(get("/admin/reservas").principal(AUTH).param("estado", "JUGADA"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.estado").exists());
            mvc.perform(get("/admin/reservas").principal(AUTH).param("desde", "31/10/2026"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.desde").exists());
        }

        @Test
        void losErroresDelServicioSeRespetan() throws Exception {
            when(reservaConsultaService.listar(any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyInt(), any(),
                    any(), any())).thenThrow(new com.LosCiruelos.padel_club_api.common.exception.ReservaException(
                            "El tamaño de página tiene que estar entre 1 y 100."));

            mvc.perform(get("/admin/reservas").principal(AUTH).param("size", "500"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("El tamaño de página tiene que estar entre 1 y 100."));
        }

        @Test
        void buscaClientes() throws Exception {
            when(usuarioAdminService.buscarClientes("ana", true)).thenReturn(List.of(
                    new com.LosCiruelos.padel_club_api.usuarios.dto.response.ClienteBusquedaResponse(5L, "Ana", "Gómez",
                            "ana@test.com", "5491122334455", true, null, null, false, true)));

            mvc.perform(get("/admin/usuarios/clientes").principal(AUTH).param("buscar", "ana"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(5))
                    .andExpect(jsonPath("$[0].telefonoVerificado").value(true))
                    .andExpect(jsonPath("$[0].perfilCompleto").value(false))
                    .andExpect(jsonPath("$[0].enabled").value(true));

            mvc.perform(get("/admin/usuarios/clientes").principal(AUTH).param("buscar", "ana").param("soloActivos", "false"))
                    .andExpect(status().isOk());
            verify(usuarioAdminService).buscarClientes("ana", false);
        }

        @Test
        void buscarEsObligatorio() throws Exception {
            mvc.perform(get("/admin/usuarios/clientes").principal(AUTH))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.buscar").exists());
        }
    }

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Bonificaciones {

        @Test
        void bonificarSinBodyEsTodoLoQueFalta() throws Exception {
            when(cobroService.bonificar(5L, null, null, null, EMAIL)).thenReturn(new PagoResponse());

            mvc.perform(post("/admin/reservas/5/bonificacion").principal(AUTH)).andExpect(status().isOk());
            verify(cobroService).bonificar(5L, null, null, null, EMAIL);
        }

        @Test
        void bonificarUnaParteConMotivo() throws Exception {
            when(cobroService.bonificar(any(), any(), any(), any(), any())).thenReturn(new PagoResponse());

            mvc.perform(json(post("/admin/reservas/5/bonificacion"), "{\"monto\":7500,\"motivo\":\"socio\"}"))
                    .andExpect(status().isOk());
            verify(cobroService).bonificar(5L, new BigDecimal("7500"), "socio", null, EMAIL);

            mvc.perform(json(post("/admin/reservas/5/bonificacion"), "{\"monto\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.monto").exists());
        }

        @Test
        void unEmpleadoNoPuedeBonificar() throws Exception {
            when(cobroService.bonificar(any(), any(), any(), any(), any())).thenThrow(
                    new com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException(
                            "Solo el ADMIN puede bonificar una reserva."));

            mvc.perform(post("/admin/reservas/5/bonificacion").principal(AUTH))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Solo el ADMIN puede bonificar una reserva."));
        }

        @Test
        void crearConBonificacionVaPorElServicioDePagos() throws Exception {
            ReservaResponse r = new ReservaResponse();
            r.setId(9L);
            when(cobroService.crearReservaBonificada(any(), eq(EMAIL))).thenReturn(r);

            mvc.perform(json(post("/admin/reservas"), """
                    {"canchaId":1,"fechaReserva":"2099-01-01","horaInicio":"18:00","horaFin":"19:30",
                     "clienteId":7,"bonificacion":{"motivo":"torneo"}}
                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(9));
            verify(reservaService, never()).crearParaCliente(any(), anyString());
        }

        @Test
        void elPersonalCreaUnaReservaParaUnCliente() throws Exception {
            ReservaResponse r = new ReservaResponse();
            r.setId(10L);
            when(reservaService.crearParaCliente(any(), eq(EMAIL))).thenReturn(r);

            mvc.perform(json(post("/admin/reservas"), """
                    {"canchaId":1,"fechaReserva":"2099-01-01","horaInicio":"18:00","horaFin":"19:30","clienteId":7}
                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(10));
            verify(cobroService, never()).crearReservaBonificada(any(), anyString());
        }
    }

    @Nested
    class Ventas {

        @Test
        void registrarDevuelve201() throws Exception {
            when(ventaService.registrar(any(), eq(EMAIL))).thenReturn(new VentaResponse(4L, TestData.AHORA,
                    new java.math.BigDecimal("3000.00"), MedioDePago.EFECTIVO, null, null, "Ana Pérez", List.of(), null, null,
                    com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta.COBRADA, null, null, null,
                    new java.math.BigDecimal("0.00"), List.of(), null));

            mvc.perform(json(post("/admin/ventas"), """
                    {"medio":"EFECTIVO","items":[{"productoId":5,"cantidad":2},{"nombre":"Gatorade","precio":2500,"cantidad":1}]}
                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(4))
                    .andExpect(jsonPath("$.total").value(3000.00))
                    .andExpect(jsonPath("$.fecha").value("2026-10-13T10:00:00.000-03:00"));
        }

        @Test
        void validaLaVenta() throws Exception {
            mvc.perform(json(post("/admin/ventas"), "{\"items\":[]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.items").exists())
                    .andExpect(jsonPath("$.errors.medio").exists());
            mvc.perform(json(post("/admin/ventas"), "{\"medio\":\"EFECTIVO\",\"items\":[{\"productoId\":5,\"cantidad\":0}]}"))
                    .andExpect(status().isBadRequest());
            verify(ventaService, never()).registrar(any(), anyString());
        }

        @Test
        void elAdminVerificaUnaVentaConCuentaDni() throws Exception {
            mvc.perform(json(put("/admin/ventas/8/verificacion"), "{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.verificada").exists());
            when(ventaService.verificar(8L, true, EMAIL)).thenReturn(new VentaResponse(8L, TestData.AHORA,
                    new java.math.BigDecimal("3000.00"), MedioDePago.CUENTA_DNI, null, null, "Ana Pérez", List.of(),
                    true, TestData.AHORA, com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta.COBRADA, null,
                    null, null, new java.math.BigDecimal("0.00"), List.of(), null));

            mvc.perform(json(put("/admin/ventas/8/verificacion"), "{\"verificada\":true}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verificada").value(true));
        }

        private VentaResponse anulada() {
            return new VentaResponse(8L, TestData.AHORA, new java.math.BigDecimal("3000.00"), MedioDePago.EFECTIVO, 3L,
                    "Juan Gómez", "Ana Pérez", List.of(), null, null,
                    com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta.ANULADA, TestData.AHORA, "Ana Pérez",
                    "Error de carga", new java.math.BigDecimal("0.00"), List.of(), null);
        }

        @Test
        void anularPideElMotivo() throws Exception {
            mvc.perform(json(post("/admin/ventas/8/anulacion"), "{\"motivo\":\"  \"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.motivo").value("Indicá por qué se anula la venta"));
            mvc.perform(json(post("/admin/ventas/8/anulacion"), "{\"motivo\":\"" + "x".repeat(201) + "\"}"))
                    .andExpect(status().isBadRequest());
            verify(ventaService, never()).anular(any(), anyString(), anyString());

            when(ventaService.anular(8L, "Error de carga", EMAIL)).thenReturn(anulada());
            mvc.perform(json(post("/admin/ventas/8/anulacion"), "{\"motivo\":\"Error de carga\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("ANULADA"))
                    .andExpect(jsonPath("$.anuladaPor").value("Ana Pérez"))
                    .andExpect(jsonPath("$.motivoAnulacion").value("Error de carga"));

            when(ventaService.anular(9L, "Error", EMAIL)).thenThrow(
                    new com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException(
                            "Solo podés anular ventas de hoy."));
            mvc.perform(json(post("/admin/ventas/9/anulacion"), "{\"motivo\":\"Error\"}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Solo podés anular ventas de hoy."));
        }

        @Test
        void devolverValidaLoQueSeDevuelve() throws Exception {
            mvc.perform(json(post("/admin/ventas/8/devoluciones"), "{\"items\":[]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.motivo").exists())
                    .andExpect(jsonPath("$.errors.items").exists());
            mvc.perform(json(post("/admin/ventas/8/devoluciones"),
                    "{\"motivo\":\"Vencido\",\"items\":[{\"productoId\":5,\"cantidad\":0}]}"))
                    .andExpect(status().isBadRequest());
            verify(ventaService, never()).devolver(any(), any(), anyString());

            VentaResponse.Devolucion devolucion = new VentaResponse.Devolucion(30L, TestData.AHORA, "Admin Club",
                    "Vencido", new java.math.BigDecimal("1500.00"),
                    List.of(new VentaResponse.ItemDevuelto(5L, "Agua", 1, false)));
            when(ventaService.devolver(eq(8L), any(), eq(EMAIL))).thenReturn(new VentaResponse(8L, TestData.AHORA,
                    new java.math.BigDecimal("3000.00"), MedioDePago.EFECTIVO, null, null, "Ana Pérez", List.of(), null,
                    null, com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta.COBRADA, null, null, null, new java.math.BigDecimal("1500.00"),
                    List.of(devolucion), null));
            mvc.perform(json(post("/admin/ventas/8/devoluciones"),
                    "{\"motivo\":\"Vencido\",\"items\":[{\"productoId\":5,\"cantidad\":1,\"vuelveAlStock\":false}]}"))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.montoDevuelto").value(1500.00))
                    .andExpect(jsonPath("$.devoluciones[0].items[0].vuelveAlStock").value(false));
            org.mockito.ArgumentCaptor<com.LosCiruelos.padel_club_api.ventas.dto.request.DevolucionVentaRequest> req =
                    org.mockito.ArgumentCaptor.forClass(com.LosCiruelos.padel_club_api.ventas.dto.request.DevolucionVentaRequest.class);
            verify(ventaService).devolver(eq(8L), req.capture(), eq(EMAIL));
            org.assertj.core.api.Assertions.assertThat(req.getValue().getItems().get(0).getCantidad()).isEqualTo(1);
        }

        @Test
        void losMediosDeCobroNoSeConfundenConUnaVenta() throws Exception {
            when(ventaConsultaService.medios()).thenReturn(List.of(
                    new com.LosCiruelos.padel_club_api.ventas.dto.response.MedioDeVentaResponse(MedioDePago.EFECTIVO,
                            "Efectivo", true, false, null),
                    new com.LosCiruelos.padel_club_api.ventas.dto.response.MedioDeVentaResponse(MedioDePago.MERCADO_PAGO,
                            "Mercado Pago (QR de la caja)", false, true,
                            "El QR de la caja de Mercado Pago no está configurado.")));

            // antes caía en GET /admin/ventas/{id} y respondía 400
            mvc.perform(get("/admin/ventas/medios").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].medio").value("EFECTIVO"))
                    .andExpect(jsonPath("$[0].detalle").doesNotExist())
                    .andExpect(jsonPath("$[1].etiqueta").value("Mercado Pago (QR de la caja)"))
                    .andExpect(jsonPath("$[1].disponible").value(false))
                    .andExpect(jsonPath("$[1].conQr").value(true))
                    .andExpect(jsonPath("$[1].detalle").value("El QR de la caja de Mercado Pago no está configurado."));
            verify(ventaConsultaService, never()).obtener(any(), anyString());
        }

        @Test
        void cancelarElQrDeUnaVenta() throws Exception {
            when(ventaQrService.cancelar(8L, EMAIL)).thenReturn(new VentaResponse(8L, TestData.AHORA,
                    new java.math.BigDecimal("3000.00"), MedioDePago.MERCADO_PAGO, null, null, "Ana Pérez", List.of(),
                    null, null, com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta.NO_COBRADA, null, null,
                    null, new java.math.BigDecimal("0.00"), List.of(),
                    new VentaResponse.Qr("ORD1", TestData.AHORA.plusMinutes(10))));

            mvc.perform(delete("/admin/ventas/8/qr").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("NO_COBRADA"))
                    .andExpect(jsonPath("$.qr.orderId").value("ORD1"))
                    .andExpect(jsonPath("$.qr.venceAt").value("2026-10-13T10:10:00.000-03:00"));

            when(ventaQrService.cancelar(9L, EMAIL)).thenThrow(
                    new com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException(
                            "La venta no está esperando el pago con QR."));
            mvc.perform(delete("/admin/ventas/9/qr").principal(AUTH))
                    .andExpect(status().isConflict());
        }

        @Test
        void cambiarElClienteYVerUnaVenta() throws Exception {
            when(ventaService.cambiarCliente(8L, null, EMAIL)).thenReturn(anulada());
            mvc.perform(json(put("/admin/ventas/8/cliente"), "{\"clienteId\":null}"))
                    .andExpect(status().isOk());
            verify(ventaService).cambiarCliente(8L, null, EMAIL);
            when(ventaService.cambiarCliente(8L, 3L, EMAIL)).thenReturn(anulada());
            mvc.perform(json(put("/admin/ventas/8/cliente"), "{\"clienteId\":3}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.clienteId").value(3));

            when(ventaConsultaService.obtener(8L, EMAIL)).thenReturn(anulada());
            mvc.perform(get("/admin/ventas/8").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("ANULADA"));
            when(ventaConsultaService.obtener(9L, EMAIL)).thenThrow(
                    new com.LosCiruelos.padel_club_api.ventas.exception.VentaNoEncontradaException(9L));
            mvc.perform(get("/admin/ventas/9").principal(AUTH))
                    .andExpect(status().isNotFound());
        }

        @Test
        void listarPasaLosFiltrosYDevuelveLaPaginaConLosTotales() throws Exception {
            var porMedio = new java.util.EnumMap<MedioDePago, java.math.BigDecimal>(MedioDePago.class);
            porMedio.put(MedioDePago.EFECTIVO, new java.math.BigDecimal("4500.00"));
            when(ventaConsultaService.listar(any(), any(), any(), any(), any(), any(), any(), anyInt(), any(), any(),
                    eq(EMAIL))).thenReturn(new com.LosCiruelos.padel_club_api.ventas.dto.response.ListadoVentasResponse(
                            List.of(), 1, 20, 21, 2, new com.LosCiruelos.padel_club_api.ventas.dto.response.ListadoVentasResponse
                                    .Totales(20, 1, new java.math.BigDecimal("4500.00"), new java.math.BigDecimal("1500.00"),
                                            porMedio, null)));

            mvc.perform(get("/admin/ventas").param("desde", "2026-10-01").param("hasta", "2026-10-31")
                    .param("productoId", "5").param("cliente", "pérez").param("clienteId", "3")
                    .param("medio", "EFECTIVO").param("medio", "CUENTA_DNI").param("estado", "COBRADA,ANULADA")
                    .param("page", "1").param("size", "20").param("orden", "asc").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.pagina").value(1))
                    .andExpect(jsonPath("$.totalElementos").value(21))
                    .andExpect(jsonPath("$.totales.ventas").value(20))
                    .andExpect(jsonPath("$.totales.anuladas").value(1))
                    .andExpect(jsonPath("$.totales.devuelto").value(1500.00))
                    .andExpect(jsonPath("$.totales.porMedio.EFECTIVO").value(4500.00));
            verify(ventaConsultaService).listar(java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 31),
                    5L, "pérez", 3L, List.of(MedioDePago.EFECTIVO, MedioDePago.CUENTA_DNI),
                    List.of(com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta.COBRADA,
                            com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta.ANULADA),
                    1, 20, "asc", EMAIL);

            mvc.perform(get("/admin/ventas").param("medio", "BITCOIN").principal(AUTH))
                    .andExpect(status().isBadRequest());
        }
    }
}
