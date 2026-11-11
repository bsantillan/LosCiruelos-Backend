package com.LosCiruelos.padel_club_api.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.controller.ClubController;
import com.LosCiruelos.padel_club_api.club.controller.ConfiguracionAdminController;
import com.LosCiruelos.padel_club_api.club.dto.response.ConfiguracionResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.HorariosClubResponse;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionGeneralService;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.common.Reloj;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

/** Las rutas públicas con la configuración de seguridad real: se pueden pedir sin sesión. */
@SpringJUnitWebConfig(RutasPublicasTest.Config.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class RutasPublicasTest {

    @Configuration
    @EnableWebMvc
    @Import(SecurityConfig.class)
    static class Config {

        /** Sin token: el filtro de JWT deja pasar el pedido sin autenticar. */
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
        ConfiguracionGeneralService configuracionGeneralService() {
            ConfiguracionGeneralService servicio = mock(ConfiguracionGeneralService.class);
            when(servicio.horarios()).thenReturn(new HorariosClubResponse(List.of(
                    new ConfiguracionResponse.Horario("Lunes", LocalTime.of(8, 0), LocalTime.of(23, 0))), List.of(), 90,
                    180));
            return servicio;
        }

        @Bean
        ClubController clubController(ConfiguracionGeneralService servicio) {
            return new ClubController(servicio);
        }

        @Bean
        ConfiguracionAdminController configuracionAdminController(ConfiguracionGeneralService servicio) {
            return new ConfiguracionAdminController(mock(ConfiguracionService.class), servicio);
        }
    }

    @Autowired
    private WebApplicationContext contexto;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
    }

    @Test
    void losHorariosDelClubSonPublicos() throws Exception {
        mvc.perform(get("/club/horarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios[0].dia").value("Lunes"))
                .andExpect(jsonPath("$.duracionMinimaTurno").value(90));
    }

    @Test
    void laConfiguracionDelAdminNo() throws Exception {
        mvc.perform(get("/admin/configuracion"))
                .andExpect(status().isUnauthorized());
    }
}
