package com.LosCiruelos.padel_club_api.ventas.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.LosCiruelos.padel_club_api.ventas.dto.request.AnulacionVentaRequest;
import com.LosCiruelos.padel_club_api.ventas.dto.request.DevolucionVentaRequest;
import com.LosCiruelos.padel_club_api.ventas.service.VentaConsultaService;
import com.LosCiruelos.padel_club_api.ventas.service.VentaService;

/** Quién puede anular y devolver una venta (los @PreAuthorize, con la seguridad de métodos real). */
@SpringJUnitConfig(VentaAdminControllerRolesTest.Config.class)
class VentaAdminControllerRolesTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        VentaAdminController ventaAdminController() {
            return new VentaAdminController(mock(VentaService.class), mock(VentaConsultaService.class));
        }
    }

    @Autowired
    private VentaAdminController controller;

    private final TestingAuthenticationToken auth = new TestingAuthenticationToken("x@test.com", null);

    private void devolver() {
        controller.devolver(8L, new DevolucionVentaRequest(), auth);
    }

    private void anular() {
        controller.anular(8L, new AnulacionVentaRequest(), auth);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void elAdminAnulaYDevuelve() {
        anular();
        devolver();
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void elEmpleadoAnulaPeroNoDevuelve() {
        anular();
        assertThatThrownBy(this::devolver).isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void elClienteNinguna() {
        assertThatThrownBy(this::anular).isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(this::devolver).isInstanceOf(AuthorizationDeniedException.class);
    }
}
