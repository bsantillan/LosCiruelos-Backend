package com.LosCiruelos.padel_club_api.Controller;

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

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.DTOs.Responses.CanchaResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.DisponibilidadResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.LoginResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PaginaResponse;
import com.LosCiruelos.padel_club_api.pagos.controller.PagoController;
import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PerfilResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ProductoResponse;
import com.LosCiruelos.padel_club_api.reservas.service.DisponibilidadService;
import com.LosCiruelos.padel_club_api.reservas.controller.ReservaAdminController;
import com.LosCiruelos.padel_club_api.reservas.controller.ReservaController;
import com.LosCiruelos.padel_club_api.reservas.service.StockService;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.UsuarioAdminResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.NotificacionesResponse;
import com.LosCiruelos.padel_club_api.Entities.Enum.CanalNotificacion;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.OpcionReembolso;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.TokenType;
import com.LosCiruelos.padel_club_api.Exceptions.HorarioOcupadoException;
import com.LosCiruelos.padel_club_api.Exceptions.PagoNoEncontradoException;
import com.LosCiruelos.padel_club_api.Exceptions.PagoProveedorException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaNoEncontradaException;
import com.LosCiruelos.padel_club_api.Exceptions.StockInsuficienteException;
import com.LosCiruelos.padel_club_api.Handlers.GlobalExceptionHandler;
import com.LosCiruelos.padel_club_api.Services.AdminService;
import com.LosCiruelos.padel_club_api.Services.AuthService;
import com.LosCiruelos.padel_club_api.Services.CanchaService;
import com.LosCiruelos.padel_club_api.reservas.service.CancelacionService;
import com.LosCiruelos.padel_club_api.Services.NotificacionesService;
import com.LosCiruelos.padel_club_api.pagos.service.CobroService;
import com.LosCiruelos.padel_club_api.pagos.service.PagoOnlineService;
import com.LosCiruelos.padel_club_api.Services.PasswordResetService;
import com.LosCiruelos.padel_club_api.Services.PerfilService;
import com.LosCiruelos.padel_club_api.Services.ProductoService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;
import com.LosCiruelos.padel_club_api.Services.ResumenService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaConsultaService;
import com.LosCiruelos.padel_club_api.Services.BusquedaClientesService;
import com.LosCiruelos.padel_club_api.Services.VerificationService;
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
    private MercadoPagoWebhookValidator webhookValidator;
    private AuthService authService;
    private VerificationService verificationService;
    private PasswordResetService passwordResetService;
    private PerfilService perfilService;
    private AdminService adminService;
    private CanchaService canchaService;
    private DisponibilidadService disponibilidadService;
    private ProductoService productoService;
    private StockService stockService;
    private NotificacionesService notificacionesService;
    private ResumenService resumenService;
    private ReservaConsultaService reservaConsultaService;
    private BusquedaClientesService busquedaClientesService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        reservaService = mock(ReservaService.class);
        cancelacionService = mock(CancelacionService.class);
        pagoOnlineService = mock(PagoOnlineService.class);
        cobroService = mock(CobroService.class);
        webhookValidator = mock(MercadoPagoWebhookValidator.class);
        authService = mock(AuthService.class);
        verificationService = mock(VerificationService.class);
        passwordResetService = mock(PasswordResetService.class);
        perfilService = mock(PerfilService.class);
        adminService = mock(AdminService.class);
        canchaService = mock(CanchaService.class);
        disponibilidadService = mock(DisponibilidadService.class);
        productoService = mock(ProductoService.class);
        stockService = mock(StockService.class);
        notificacionesService = mock(NotificacionesService.class);
        resumenService = mock(ResumenService.class);
        reservaConsultaService = mock(ReservaConsultaService.class);
        busquedaClientesService = mock(BusquedaClientesService.class);

        mvc = MockMvcBuilders.standaloneSetup(
                new ReservaController(reservaService, reservaConsultaService, cancelacionService, cobroService),
                new PagoController(pagoOnlineService, cobroService, webhookValidator),
                new AuthController(authService, verificationService, passwordResetService),
                new PerfilController(perfilService),
                new AdminController(adminService),
                new CanchaController(canchaService, disponibilidadService),
                new ProductoController(productoService, stockService),
                new AdminProductoController(productoService),
                new NotificacionesController(notificacionesService),
                new ResumenController(resumenService),
                new ReservaAdminController(reservaConsultaService),
                new ClienteAdminController(busquedaClientesService))
                .setControllerAdvice(new GlobalExceptionHandler(TestData.reloj(TestData.AHORA)))
                // mismo formato de fechas que la app
                .setMessageConverters(new JacksonJsonHttpMessageConverter(
                        JsonMapper.builder().addModule(Fechas.moduloJackson()).build()))
                .build();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
        return req.contentType(MediaType.APPLICATION_JSON).content(body).principal(AUTH);
    }

    private static final String RESERVA_VALIDA = """
            {"canchaId":1,"fechaReserva":"2099-01-01","horaInicio":"18:00","horaFin":"19:30","cantidadPelotas":2,"cantidadPaletas":4}
            """;

    // ─────────────────────────────────────────────────────────────────────

    @Nested
    class Reservas {

        @Test
        void crearDevuelve201() throws Exception {
            ReservaResponse r = new ReservaResponse();
            r.setId(5L);
            r.setEstado(EstadoReserva.PENDIENTE);
            when(reservaService.crearReserva(any(), eq(EMAIL))).thenReturn(r);

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
            when(reservaService.crearReserva(any(), anyString())).thenThrow(new HorarioOcupadoException());

            mvc.perform(json(post("/reservas"), RESERVA_VALIDA))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").exists());
        }

        @Test
        void elPersonalEligeElReembolsoAlCancelar() throws Exception {
            ReservaResponse cancelada = new ReservaResponse();
            cancelada.setEstado(EstadoReserva.CANCELADA);
            when(cancelacionService.cancelar(4L, EMAIL, OpcionReembolso.SENIA)).thenReturn(cancelada);

            mvc.perform(delete("/reservas/4/cancelar").param("reembolso", "SENIA").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.estado").value("CANCELADA"));
            mvc.perform(delete("/reservas/4/cancelar").param("reembolso", "MITAD").principal(AUTH))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void reservaAjenaDevuelve404() throws Exception {
            when(reservaConsultaService.obtener(7L, EMAIL)).thenThrow(new ReservaNoEncontradaException(7L));

            mvc.perform(get("/reservas/7").principal(AUTH)).andExpect(status().isNotFound());
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

            verify(cancelacionService).cancelar(3L, EMAIL, null);
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
            mvc.perform(json(post("/pagos/4/pago-efectivo"), "{\"monto\":-1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.monto").exists());
            mvc.perform(json(post("/pagos/4/pago-efectivo"), "{}")).andExpect(status().isBadRequest());

            when(cobroService.registrarPagoManual(any(), any(), any(), any(), anyString())).thenReturn(new PagoResponse());
            mvc.perform(json(post("/pagos/4/pago-efectivo"), "{\"monto\":7500}")).andExpect(status().isOk());
            mvc.perform(json(post("/pagos/4/pago-presencial"), "{\"monto\":7500}")).andExpect(status().isOk());
            verify(cobroService).registrarPagoManual(4L, new BigDecimal("7500"), PaymentProvider.MANUAL, null, EMAIL);
            verify(cobroService).registrarPagoManual(4L, new BigDecimal("7500"), PaymentProvider.MERCADO_PAGO, null, EMAIL);
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
            when(adminService.crearUsuario(any())).thenReturn(new UsuarioAdminResponse());
            when(adminService.editarUsuario(eq(2L), any())).thenReturn(new UsuarioAdminResponse());
            when(adminService.listarUsuarios(any())).thenReturn(List.of());
            when(adminService.getUsuario(2L)).thenReturn(new UsuarioAdminResponse());
            when(adminService.reactivarUsuario(2L)).thenReturn(new UsuarioAdminResponse());
            when(adminService.desactivarUsuario(2L)).thenReturn(new UsuarioAdminResponse());
            String body = "{\"email\":\"e@b.com\",\"nombre\":\"Emp\",\"apellido\":\"Leado\",\"telefono\":\"1122334455\",\"rol\":\"EMPLEADO\"}";

            mvc.perform(json(post("/admin/usuarios"), body)).andExpect(status().isCreated());
            mvc.perform(json(put("/admin/usuarios/2"), body)).andExpect(status().isOk());
            mvc.perform(get("/admin/usuarios").param("rol", "CLIENTE")).andExpect(status().isOk());
            mvc.perform(get("/admin/usuarios/2")).andExpect(status().isOk());
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
            when(productoService.listarActivos()).thenReturn(List.of(new ProductoResponse()));
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
            mvc.perform(delete("/admin/configuracion/productos/1")).andExpect(status().isOk());
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
            when(reservaConsultaService.devolucionesPendientes()).thenReturn(List.of(new ReservaResponse()));
            when(reservaService.registrarDevolucion(5L, 2, EMAIL)).thenReturn(new ReservaResponse());

            mvc.perform(get("/reservas/devoluciones-pendientes").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
            mvc.perform(json(post("/reservas/5/devolucion"), "{\"paletasDevueltas\":2}")).andExpect(status().isOk());
            mvc.perform(json(post("/reservas/5/devolucion"), "{\"paletasDevueltas\":-1}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.paletasDevueltas").exists());
            verify(reservaService).registrarDevolucion(5L, 2, EMAIL);
        }

        @Test
        void saldosPendientes() throws Exception {
            when(reservaConsultaService.saldosPendientes()).thenReturn(List.of(new ReservaResponse(), new ReservaResponse()));

            mvc.perform(get("/reservas/saldos-pendientes").principal(AUTH))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }

        @Test
        void stockInsuficienteDevuelve409() throws Exception {
            when(reservaService.crearReserva(any(), anyString()))
                    .thenThrow(new StockInsuficienteException("Solo quedan 2 paletas disponibles para ese horario."));

            mvc.perform(json(post("/reservas"), RESERVA_VALIDA))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error").value("Solo quedan 2 paletas disponibles para ese horario."));
        }

        @Test
        void cantidadesInvalidasEnLaReserva() throws Exception {
            mvc.perform(json(post("/reservas"), """
                    {"canchaId":1,"fechaReserva":"2099-01-01","horaInicio":"18:00","horaFin":"19:30","cantidadPelotas":-1,"cantidadPaletas":99}
                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.cantidadPelotas").exists())
                    .andExpect(jsonPath("$.errors.cantidadPaletas").exists());
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
            mvc.perform(json(put("/admin/configuracion/avisos"), "{\"recordatorioActivo\":true,\"minutosRecordatorio\":5}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.minutosRecordatorio").exists());
            mvc.perform(json(put("/admin/configuracion/avisos"), "{\"recordatorioActivo\":true,\"minutosRecordatorio\":120}"))
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
            when(resumenService.resumen(desde, hasta, false)).thenReturn(new com.LosCiruelos.padel_club_api.DTOs.Responses.ResumenResponse(
                    desde, hasta,
                    new com.LosCiruelos.padel_club_api.DTOs.Responses.ResumenResponse.Jugados(28,
                            new BigDecimal("420000.00"), new BigDecimal("380000.00"), new BigDecimal("15000.00"),
                            new BigDecimal("25000.00")),
                    new com.LosCiruelos.padel_club_api.DTOs.Responses.ResumenResponse.PorJugar(6,
                            new BigDecimal("90000.00"), new BigDecimal("45000.00"), new BigDecimal("0.00"),
                            new BigDecimal("45000.00")),
                    new com.LosCiruelos.padel_club_api.DTOs.Responses.ResumenResponse.Caja(
                            new BigDecimal("230000.00"), new BigDecimal("90000.00"), new BigDecimal("100000.00"),
                            new BigDecimal("40000.00"), new BigDecimal("7500.00"), new BigDecimal("222500.00")),
                    new com.LosCiruelos.padel_club_api.DTOs.Responses.ResumenResponse.Cancelaciones(3,
                            new BigDecimal("7500.00")),
                    List.of(new com.LosCiruelos.padel_club_api.DTOs.Resumen.ProductoVendido(1L, "Pelotas",
                            com.LosCiruelos.padel_club_api.Entities.Enum.TipoProducto.VENTA, 40L,
                            new BigDecimal("200000.00")))));

            mvc.perform(get("/admin/resumen").principal(ADMIN).param("desde", "2026-10-01").param("hasta", "2026-10-31"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.desde").value("2026-10-01"))
                    .andExpect(jsonPath("$.hasta").value("2026-10-31"))
                    .andExpect(jsonPath("$.jugados.turnos").value(28))
                    .andExpect(jsonPath("$.jugados.montoTotal").value(420000.00))
                    .andExpect(jsonPath("$.jugados.montoCobrado").value(380000.00))
                    .andExpect(jsonPath("$.jugados.montoBonificado").value(15000.00))
                    .andExpect(jsonPath("$.jugados.deuda").value(25000.00))
                    .andExpect(jsonPath("$.porJugar.turnos").value(6))
                    .andExpect(jsonPath("$.porJugar.cobradoAnticipado").value(45000.00))
                    .andExpect(jsonPath("$.porJugar.aCobrar").value(45000.00))
                    .andExpect(jsonPath("$.caja.total").value(230000.00))
                    .andExpect(jsonPath("$.caja.efectivo").value(90000.00))
                    .andExpect(jsonPath("$.caja.mercadoPagoOnline").value(100000.00))
                    .andExpect(jsonPath("$.caja.mercadoPagoPresencial").value(40000.00))
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
        void elEmpleadoPideSoloElDiaDeHoyYElAdminCualquierPeriodo() throws Exception {
            mvc.perform(get("/admin/resumen").principal(EMPLEADO)).andExpect(status().isOk());
            verify(resumenService).resumen(null, null, true);

            mvc.perform(get("/admin/resumen").principal(ADMIN)).andExpect(status().isOk());
            verify(resumenService).resumen(null, null, false);

            when(resumenService.resumen(any(), any(), eq(true)))
                    .thenThrow(new com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException(
                            "Solo podés ver el resumen de hoy."));
            mvc.perform(get("/admin/resumen").principal(EMPLEADO).param("desde", "2026-10-01").param("hasta", "2026-10-31"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Solo podés ver el resumen de hoy."));
        }

        @Test
        void unaFechaConFormatoInvalidoDevuelve400() throws Exception {
            mvc.perform(get("/admin/resumen").principal(ADMIN).param("desde", "01/10/2026").param("hasta", "2026-10-31"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.desde").exists());
        }

        @Test
        void unPeriodoInvalidoDevuelve400() throws Exception {
            when(resumenService.resumen(any(), any(), eq(false)))
                    .thenThrow(new com.LosCiruelos.padel_club_api.Exceptions.ReservaException(
                            "La fecha desde no puede ser posterior a hasta."));

            mvc.perform(get("/admin/resumen").principal(ADMIN).param("desde", "2026-10-31").param("hasta", "2026-10-01"))
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
                            new BigDecimal("280000.00"), new BigDecimal("15000.00"), new BigDecimal("165000.00")));
        }

        @Test
        void listaConTodosLosFiltrosYLosTotales() throws Exception {
            when(reservaConsultaService.listar(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyInt(), any(),
                    any())).thenReturn(pagina());

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
                    .andExpect(jsonPath("$.totales.cobradoMercadoPago").value(280000.00))
                    .andExpect(jsonPath("$.totales.montoBonificado").value(15000.00))
                    .andExpect(jsonPath("$.totales.montoPendiente").value(165000.00));

            verify(reservaConsultaService).listar(java.time.LocalDate.of(2026, 10, 1), java.time.LocalDate.of(2026, 10, 31),
                    List.of(EstadoReserva.SEÑADA, EstadoReserva.PARCIALMENTE_PAGADA), 7L, "pérez", 2L, null, true, 1, 50,
                    "desc");
        }

        @Test
        void sinParametrosUsaLosValoresPorDefecto() throws Exception {
            when(reservaConsultaService.listar(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyInt(), any(),
                    any())).thenReturn(pagina());

            mvc.perform(get("/admin/reservas").principal(AUTH)).andExpect(status().isOk());

            verify(reservaConsultaService).listar(null, null, null, null, null, null, null, false, 0, null, null);
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
            when(reservaConsultaService.listar(any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyInt(), any(),
                    any())).thenThrow(new com.LosCiruelos.padel_club_api.Exceptions.ReservaException(
                            "El tamaño de página tiene que estar entre 1 y 100."));

            mvc.perform(get("/admin/reservas").principal(AUTH).param("size", "500"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("El tamaño de página tiene que estar entre 1 y 100."));
        }

        @Test
        void buscaClientes() throws Exception {
            when(busquedaClientesService.buscar("ana", true)).thenReturn(List.of(
                    new com.LosCiruelos.padel_club_api.DTOs.Responses.ClienteBusquedaResponse(5L, "Ana", "Gómez",
                            "ana@test.com", "5491122334455", true, null, null, false, true)));

            mvc.perform(get("/admin/clientes").principal(AUTH).param("buscar", "ana"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(5))
                    .andExpect(jsonPath("$[0].telefonoVerificado").value(true))
                    .andExpect(jsonPath("$[0].perfilCompleto").value(false))
                    .andExpect(jsonPath("$[0].enabled").value(true));

            mvc.perform(get("/admin/clientes").principal(AUTH).param("buscar", "ana").param("soloActivos", "false"))
                    .andExpect(status().isOk());
            verify(busquedaClientesService).buscar("ana", false);
        }

        @Test
        void buscarEsObligatorio() throws Exception {
            mvc.perform(get("/admin/clientes").principal(AUTH))
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

            mvc.perform(post("/pagos/5/bonificacion").principal(AUTH)).andExpect(status().isOk());
            verify(cobroService).bonificar(5L, null, null, null, EMAIL);
        }

        @Test
        void bonificarUnaParteConMotivo() throws Exception {
            when(cobroService.bonificar(any(), any(), any(), any(), any())).thenReturn(new PagoResponse());

            mvc.perform(json(post("/pagos/5/bonificacion"), "{\"monto\":7500,\"motivo\":\"socio\"}"))
                    .andExpect(status().isOk());
            verify(cobroService).bonificar(5L, new BigDecimal("7500"), "socio", null, EMAIL);

            mvc.perform(json(post("/pagos/5/bonificacion"), "{\"monto\":0}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.monto").exists());
        }

        @Test
        void unEmpleadoNoPuedeBonificar() throws Exception {
            when(cobroService.bonificar(any(), any(), any(), any(), any())).thenThrow(
                    new com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException(
                            "Solo el ADMIN puede bonificar una reserva."));

            mvc.perform(post("/pagos/5/bonificacion").principal(AUTH))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error").value("Solo el ADMIN puede bonificar una reserva."));
        }

        @Test
        void crearConBonificacionVaPorElServicioDePagos() throws Exception {
            ReservaResponse r = new ReservaResponse();
            r.setId(9L);
            when(cobroService.crearReservaBonificada(any(), eq(EMAIL))).thenReturn(r);

            mvc.perform(json(post("/reservas"), """
                    {"canchaId":1,"fechaReserva":"2099-01-01","horaInicio":"18:00","horaFin":"19:30",
                     "clienteId":7,"bonificacion":{"motivo":"torneo"}}
                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(9));
            verify(reservaService, never()).crearReserva(any(), anyString());
        }
    }
}
