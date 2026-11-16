package com.LosCiruelos.padel_club_api.club.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.service.NovedadService;
import com.LosCiruelos.padel_club_api.common.exception.GlobalExceptionHandler;

/** Las rutas de /admin/novedades y sus validaciones. */
class NovedadAdminControllerTest {

    private final NovedadService servicio = mock(NovedadService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new NovedadAdminController(servicio))
                .setControllerAdvice(new GlobalExceptionHandler(TestData.reloj(TestData.AHORA))).build();
    }

    @Test
    void validaElTituloYLaDescripcion() throws Exception {
        mvc.perform(post("/admin/novedades").contentType(MediaType.APPLICATION_JSON).content("{\"titulo\":\"\"}")
                .principal(new UsernamePasswordAuthenticationToken("admin@test.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.titulo").value("El título es obligatorio"))
                .andExpect(jsonPath("$.errors.descripcion").value("La descripción es obligatoria"));
        mvc.perform(post("/admin/novedades").contentType(MediaType.APPLICATION_JSON)
                .content("{\"titulo\":\"Torneo\",\"descripcion\":\"Inscripción abierta\",\"visibleHasta\":\"2026-11-30\"}")
                .principal(new UsernamePasswordAuthenticationToken("admin@test.com", null)))
                .andExpect(status().isCreated());
        verify(servicio).crear(any(), eq("admin@test.com"));
    }

    @Test
    void publicacionImagenYBorrado() throws Exception {
        mvc.perform(put("/admin/novedades/3/publicacion").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/admin/novedades/3/publicacion").contentType(MediaType.APPLICATION_JSON)
                .content("{\"publicada\":true}")).andExpect(status().isOk());
        verify(servicio).publicar(3L, true);
        mvc.perform(multipart("/admin/novedades/3/imagen")
                .file(new MockMultipartFile("archivo", "a.png", "image/png", new byte[] { 1 })))
                .andExpect(status().isOk());
        mvc.perform(delete("/admin/novedades/3/imagen")).andExpect(status().isOk());
        mvc.perform(delete("/admin/novedades/3")).andExpect(status().isNoContent());
        verify(servicio).eliminar(3L);
    }
}
