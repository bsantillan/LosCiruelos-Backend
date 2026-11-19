package com.LosCiruelos.padel_club_api.club.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.LosCiruelos.padel_club_api.club.dto.response.CanchaResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.club.exception.HorariosConReservasException;
import com.LosCiruelos.padel_club_api.club.service.BajaDeCanchas;
import com.LosCiruelos.padel_club_api.club.service.CanchaAdminService;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.GlobalExceptionHandler;

/** Las rutas de /admin/canchas con el formato de errores de siempre. */
class CanchaAdminControllerTest {

    private final CanchaAdminService servicio = mock(CanchaAdminService.class);
    private final BajaDeCanchas baja = mock(BajaDeCanchas.class);
    private final UsernamePasswordAuthenticationToken admin = new UsernamePasswordAuthenticationToken("admin@test.com", null);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CanchaAdminController(servicio, baja))
                .setControllerAdvice(new GlobalExceptionHandler(com.LosCiruelos.padel_club_api.TestData.reloj(com.LosCiruelos.padel_club_api.TestData.AHORA))).build();
    }

    @Test
    void creaYValidaLaCancha() throws Exception {
        CanchaResponse r = new CanchaResponse();
        r.setId(5L);
        when(servicio.crear(any())).thenReturn(r);
        mvc.perform(post("/admin/canchas").contentType(MediaType.APPLICATION_JSON).content("""
                {"numero":5,"nombre":"Cancha central","tipo":"AIRE_LIBRE","superficie":"Césped","iluminacion":true}"""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(5));
        mvc.perform(post("/admin/canchas").contentType(MediaType.APPLICATION_JSON).content("{\"numero\":5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.tipo").value("Indicá el tipo de cancha (TECHADA o AIRE_LIBRE)"));
        when(servicio.crear(any())).thenThrow(new EstadoInvalidoException("Ya existe la cancha 1."));
        mvc.perform(post("/admin/canchas").contentType(MediaType.APPLICATION_JSON).content("""
                {"numero":1,"tipo":"TECHADA","iluminacion":true}"""))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Ya existe la cancha 1."));
    }

    @Test
    void laBajaSinConfirmarDevuelveLoQueHay() throws Exception {
        FueraDeHorarioResponse ocupado = new FueraDeHorarioResponse(
                List.of(new FueraDeHorarioResponse.Reserva(30L, LocalDate.of(2026, 10, 20), LocalTime.of(18, 0),
                        LocalTime.of(19, 30), 5, "Ana Gómez", "NORMAL")),
                List.of(new FueraDeHorarioResponse.TurnoFijo(8L, DayOfWeek.MONDAY, LocalTime.of(20, 0),
                        LocalTime.of(21, 30), 5)));
        when(baja.darDeBaja(5L, false, "admin@test.com")).thenThrow(HorariosConReservasException.porBajaDeCancha(ocupado));

        mvc.perform(put("/admin/canchas/5/baja").principal(admin))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.reservasFueraDeHorario[0].id").value(30))
                .andExpect(jsonPath("$.reservasFueraDeHorario[0].clienteNombre").value("Ana Gómez"))
                .andExpect(jsonPath("$.turnosFijosFueraDeHorario[0].id").value(8));
        mvc.perform(put("/admin/canchas/5/baja").param("confirmar", "true").principal(admin))
                .andExpect(status().isOk());
        verify(baja).darDeBaja(5L, true, "admin@test.com");
    }

    @Test
    void lasFotosVanEnMultipartYSeBorranYOrdenan() throws Exception {
        when(servicio.agregarFoto(eq(5L), any())).thenReturn(new CanchaResponse());
        mvc.perform(multipart("/admin/canchas/5/fotos")
                .file(new MockMultipartFile("archivo", "a.png", "image/png", new byte[] { 1 })))
                .andExpect(status().isOk());
        mvc.perform(multipart("/admin/canchas/5/fotos")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Mandá la imagen en el campo \"archivo\"."));
        mvc.perform(delete("/admin/canchas/5/fotos/7")).andExpect(status().isOk());
        verify(servicio).sacarFoto(5L, 7L);
        mvc.perform(put("/admin/canchas/5/fotos/orden").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fotos\":[3,1,2]}")).andExpect(status().isOk());
        verify(servicio).ordenarFotos(5L, List.of(3L, 1L, 2L));
        mvc.perform(delete("/admin/canchas/5")).andExpect(status().isNoContent());
    }
}
