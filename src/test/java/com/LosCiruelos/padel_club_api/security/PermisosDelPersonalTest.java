package com.LosCiruelos.padel_club_api.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.pagos.service.CobroService;
import com.LosCiruelos.padel_club_api.reservas.controller.ReservaAdminController;
import com.LosCiruelos.padel_club_api.reservas.service.CancelacionService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaConsultaService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;
import com.LosCiruelos.padel_club_api.usuarios.controller.UsuarioAdminController;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioAdminService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/**
 * Qué puede cada rol del personal, con la seguridad real (rutas de SecurityConfig y @PreAuthorize).
 * Las reglas por fecha de las reservas (el EMPLEADO, de hoy en adelante) están en los tests de los
 * services: ReservaConsultaServiceTest y ReservaServiceTest.
 */
@SpringJUnitWebConfig(PermisosDelPersonalTest.Config.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class PermisosDelPersonalTest {

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class Config {

        /** El usuario lo pone el test: el filtro de JWT deja pasar el pedido. */
        @Bean
        JWTFilter jwtFilter() throws Exception {
            JWTFilter filtro = mock(JWTFilter.class);
            doAnswer(inv -> {
                inv.<FilterChain>getArgument(2).doFilter(inv.<ServletRequest>getArgument(0),
                        inv.<ServletResponse>getArgument(1));
                return null;
            }).when(filtro).doFilter(any(), any(), any());
            return filtro;
        }

        @Bean
        Reloj reloj() {
            return TestData.reloj(TestData.AHORA);
        }

        @Bean
        com.LosCiruelos.padel_club_api.club.controller.JornadaController jornadaController() {
            return new com.LosCiruelos.padel_club_api.club.controller.JornadaController(
                    TestData.jornadas(TestData.reloj(TestData.AHORA)),
                    new com.LosCiruelos.padel_club_api.mapper.ConfiguracionMapper());
        }

        @Bean
        UsuarioAdminController usuarioAdminController() {
            return new UsuarioAdminController(mock(UsuarioAdminService.class));
        }

        @Bean
        ReservaAdminController reservaAdminController() {
            return new ReservaAdminController(mock(ReservaService.class), mock(ReservaConsultaService.class),
                    mock(CancelacionService.class), mock(CobroService.class));
        }
    }

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mvc;

    private static final RequestPostProcessor ADMIN = user("admin@test.com").roles("ADMIN");
    private static final RequestPostProcessor EMPLEADO = user("empleado@test.com").roles("EMPLEADO");

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder pedido, String cuerpo) {
        return pedido.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    @Test
    void elEmpleadoVeLosUsuarios() throws Exception {
        mvc.perform(get("/admin/usuarios").param("rol", "CLIENTE").with(EMPLEADO)).andExpect(status().isOk());
        mvc.perform(get("/admin/usuarios/2").with(EMPLEADO)).andExpect(status().isOk());
        mvc.perform(get("/admin/usuarios/clientes").param("buscar", "ana").with(EMPLEADO)).andExpect(status().isOk());
    }

    @Test
    void elEmpleadoNoCreaNiEditaNiDesactivaUsuarios() throws Exception {
        String usuario = """
                {"nombre":"Ana","apellido":"Gómez","email":"ana@test.com","password":"Prueba123!","rol":"CLIENTE"}""";
        mvc.perform(json(post("/admin/usuarios"), usuario).with(EMPLEADO)).andExpect(status().isForbidden());
        mvc.perform(json(put("/admin/usuarios/2"), usuario).with(EMPLEADO)).andExpect(status().isForbidden());
        mvc.perform(put("/admin/usuarios/2/reactivar").with(EMPLEADO)).andExpect(status().isForbidden());
        mvc.perform(delete("/admin/usuarios/2/desactivar").with(EMPLEADO)).andExpect(status().isForbidden());
    }

    @Test
    void elAdminHaceTodoConLosUsuarios() throws Exception {
        mvc.perform(get("/admin/usuarios").with(ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/admin/usuarios/2").with(ADMIN)).andExpect(status().isOk());
        mvc.perform(put("/admin/usuarios/2/reactivar").with(ADMIN)).andExpect(status().isOk());
        mvc.perform(delete("/admin/usuarios/2/desactivar").with(ADMIN)).andExpect(status().isOk());
    }

    @Test
    void unClienteNoVeLosUsuarios() throws Exception {
        mvc.perform(get("/admin/usuarios").with(user("c@test.com").roles("CLIENTE"))).andExpect(status().isForbidden());
    }

    @Test
    void lasReservasSonDelPersonalYLosPartidosYLasBonificacionesDelAdmin() throws Exception {
        mvc.perform(get("/admin/reservas").with(EMPLEADO)).andExpect(status().isOk());
        mvc.perform(get("/admin/reservas/devoluciones-pendientes").with(EMPLEADO)).andExpect(status().isOk());
        mvc.perform(json(post("/admin/reservas/partidos"), "{}").with(EMPLEADO)).andExpect(status().isForbidden());
        mvc.perform(json(post("/admin/reservas/4/bonificacion"), "{}").with(EMPLEADO)).andExpect(status().isForbidden());
        mvc.perform(get("/admin/reservas").with(ADMIN)).andExpect(status().isOk());
    }

    @Test
    void elPersonalVeLaJornadaEnCursoYUnClienteNo() throws Exception {
        mvc.perform(get("/admin/jornada").with(EMPLEADO)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.fecha")
                        .value("2026-10-13"));
        mvc.perform(get("/admin/jornada").with(ADMIN)).andExpect(status().isOk());
        mvc.perform(get("/admin/jornada").with(user("c@test.com").roles("CLIENTE"))).andExpect(status().isForbidden());
    }
}
