package com.LosCiruelos.padel_club_api.reservas.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.pagos.controller.PagoController;
import com.LosCiruelos.padel_club_api.reservas.dto.request.PartidoRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.request.TurnoFijoRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.response.JugadorResponse;
import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.TurnoFijoResponse;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.Exceptions.HorarioOcupadoException;
import com.LosCiruelos.padel_club_api.Handlers.GlobalExceptionHandler;
import com.LosCiruelos.padel_club_api.pagos.service.CobroService;
import com.LosCiruelos.padel_club_api.pagos.service.PagoOnlineService;
import com.LosCiruelos.padel_club_api.pagos.mercadopago.MercadoPagoWebhookValidator;
import com.LosCiruelos.padel_club_api.common.Fechas;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.OpcionReembolso;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.TipoReserva;
import com.LosCiruelos.padel_club_api.reservas.service.PartidoService;
import com.LosCiruelos.padel_club_api.reservas.service.TurnoFijoService;

import tools.jackson.databind.json.JsonMapper;

/** Partidos, turnos fijos y pagos por jugador con MockMvc standalone. */
class PartidosControllersTest {

    private static final String EMAIL = "admin@test.com";
    private static final Authentication AUTH = new UsernamePasswordAuthenticationToken(EMAIL, null, List.of());

    private PartidoService partidoService;
    private TurnoFijoService turnoFijoService;
    private PagoOnlineService pagoOnlineService;
    private CobroService cobroService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        partidoService = mock(PartidoService.class);
        turnoFijoService = mock(TurnoFijoService.class);
        pagoOnlineService = mock(PagoOnlineService.class);
        cobroService = mock(CobroService.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new PartidoController(partidoService),
                new TurnoFijoController(turnoFijoService),
                new PagoController(pagoOnlineService, cobroService, mock(MercadoPagoWebhookValidator.class)))
                .setControllerAdvice(new GlobalExceptionHandler(TestData.reloj(TestData.AHORA)))
                .setMessageConverters(new JacksonJsonHttpMessageConverter(
                        JsonMapper.builder().addModule(Fechas.moduloJackson()).build()))
                .build();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder req, String body) {
        return req.contentType(MediaType.APPLICATION_JSON).content(body).principal(AUTH);
    }

    private static final String PARTIDO = """
            {"canchaId":1,"fechaReserva":"2026-10-14","horaInicio":"18:00","horaFin":"19:30",
             "jugadores":[{"usuarioId":1},{"usuarioId":2},{"nombre":"Juan"},{"nombre":"Pedro","telefono":"11 5555-0000"}],
             "cantidadPelotas":2}
            """;

    @Test
    void crearUnPartido() throws Exception {
        ReservaResponse r = new ReservaResponse();
        r.setId(60L);
        r.setTipo(TipoReserva.PARTIDO);
        r.setEstado(EstadoReserva.CONFIRMADA);
        r.setJugadores(List.of(new JugadorResponse(201L, 1, 1L, "Ana", null, false, new BigDecimal("3750"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("3750"))));
        when(partidoService.crear(any(), eq(EMAIL))).thenReturn(r);

        mvc.perform(json(post("/admin/partidos"), PARTIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("PARTIDO"))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.jugadores[0].parte").value(3750))
                .andExpect(jsonPath("$.jugadores[0].debe").value(3750));

        ArgumentCaptor<PartidoRequest> req = ArgumentCaptor.forClass(PartidoRequest.class);
        verify(partidoService).crear(req.capture(), eq(EMAIL));
        assertThat(req.getValue().getJugadores()).hasSize(4);
        assertThat(req.getValue().getJugadores().get(3).getTelefono()).isEqualTo("11 5555-0000");
        assertThat(req.getValue().getCantidadPelotas()).isEqualTo(2);
    }

    @Test
    void sonCuatroJugadores() throws Exception {
        mvc.perform(json(post("/admin/partidos"), """
                {"canchaId":1,"fechaReserva":"2026-10-14","horaInicio":"18:00","horaFin":"19:30",
                 "jugadores":[{"usuarioId":1},{"nombre":"Juan"}]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.jugadores").value("Un partido tiene 4 jugadores"));
        verify(partidoService, never()).crear(any(), any());
    }

    @Test
    void modificarUnPartidoOcupado() throws Exception {
        when(partidoService.actualizar(eq(60L), any(), eq(EMAIL))).thenThrow(new HorarioOcupadoException());

        mvc.perform(json(put("/admin/partidos/60"), PARTIDO)).andExpect(status().isConflict());
    }

    @Test
    void elPagoDeUnJugadorLlevaSuId() throws Exception {
        when(cobroService.registrarPagoManual(any(), any(), any(), any(), any())).thenReturn(new PagoResponse());
        when(cobroService.bonificar(any(), any(), any(), any(), any())).thenReturn(new PagoResponse());

        mvc.perform(json(post("/pagos/60/pago-efectivo"), "{\"monto\":3750,\"jugadorId\":202}"))
                .andExpect(status().isOk());
        verify(cobroService).registrarPagoManual(60L, new BigDecimal("3750"), PaymentProvider.MANUAL, 202L, EMAIL);

        mvc.perform(json(post("/pagos/60/pago-presencial"), "{\"monto\":3750,\"jugadorId\":201}"))
                .andExpect(status().isOk());
        verify(cobroService).registrarPagoManual(60L, new BigDecimal("3750"), PaymentProvider.MERCADO_PAGO, 201L,
                EMAIL);

        mvc.perform(json(post("/pagos/60/bonificacion"), "{\"jugadorId\":203,\"motivo\":\"socio\"}"))
                .andExpect(status().isOk());
        verify(cobroService).bonificar(60L, null, "socio", 203L, EMAIL);
    }

    @Test
    void crearUnTurnoFijoCreaSusPartidosEInformaLasFechasOmitidas() throws Exception {
        when(turnoFijoService.crear(any(TurnoFijoRequest.class), eq(EMAIL))).thenReturn(8L);
        LocalDate ocupada = LocalDate.of(2026, 10, 28);
        when(turnoFijoService.generar(8L)).thenReturn(new TurnoFijoService.Generacion(List.of(70L),
                List.of(new TurnoFijoResponse.FechaOmitida(ocupada, "ocupado"))));
        TurnoFijoResponse tf = new TurnoFijoResponse();
        tf.setId(8L);
        when(turnoFijoService.obtener(8L)).thenReturn(tf);

        mvc.perform(json(post("/admin/turnos-fijos"), """
                {"canchaId":1,"desde":"2026-10-14","horaInicio":"20:00","horaFin":"21:30",
                 "jugadores":[{"usuarioId":1},{"nombre":"A"},{"nombre":"Bb"},{"nombre":"Cc"}]}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['jugadores[1].nombre']").exists());

        mvc.perform(json(post("/admin/turnos-fijos"), """
                {"canchaId":1,"desde":"2026-10-14","horaInicio":"20:00","horaFin":"21:30",
                 "jugadores":[{"usuarioId":1},{"nombre":"Aa"},{"nombre":"Bb"},{"nombre":"Cc"}]}
                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.fechasOmitidas[0].fecha").value("2026-10-28"))
                .andExpect(jsonPath("$.fechasOmitidas[0].motivo").value("ocupado"));
    }

    @Test
    void modificarUnTurnoFijoDevuelveLosPartidosQueQuedaronSinCambiar() throws Exception {
        when(turnoFijoService.actualizar(eq(8L), any(), eq(EMAIL))).thenReturn(List.of(71L));
        when(turnoFijoService.generar(8L)).thenReturn(new TurnoFijoService.Generacion(List.of(), List.of()));
        when(turnoFijoService.obtener(8L)).thenReturn(new TurnoFijoResponse());

        mvc.perform(json(put("/admin/turnos-fijos/8"), """
                {"canchaId":1,"desde":"2026-10-14","horaInicio":"19:00","horaFin":"20:30",
                 "jugadores":[{"usuarioId":1},{"nombre":"Aa"},{"nombre":"Bb"},{"nombre":"Cc"}]}
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partidosSinCambiar[0]").value(71));
    }

    @Test
    void bajasYConsultas() throws Exception {
        when(turnoFijoService.listar(true)).thenReturn(List.of(new TurnoFijoResponse()));
        mvc.perform(get("/admin/turnos-fijos").param("activos", "true").principal(AUTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        when(turnoFijoService.darDeBaja(8L, OpcionReembolso.TOTAL, EMAIL)).thenReturn(new TurnoFijoResponse());
        mvc.perform(delete("/admin/turnos-fijos/8").param("reembolso", "TOTAL").principal(AUTH))
                .andExpect(status().isOk());
        verify(turnoFijoService).darDeBaja(8L, OpcionReembolso.TOTAL, EMAIL);

        when(turnoFijoService.darDeBajaFecha(8L, LocalDate.of(2026, 10, 21), null, EMAIL))
                .thenReturn(new ReservaResponse());
        mvc.perform(delete("/admin/turnos-fijos/8/fechas/2026-10-21").principal(AUTH))
                .andExpect(status().isOk());
        verify(turnoFijoService).darDeBajaFecha(8L, LocalDate.of(2026, 10, 21), null, EMAIL);
    }
}
