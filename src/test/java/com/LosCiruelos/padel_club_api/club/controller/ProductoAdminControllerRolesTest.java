package com.LosCiruelos.padel_club_api.club.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.LosCiruelos.padel_club_api.club.service.ProductoService;
import com.LosCiruelos.padel_club_api.reservas.service.StockService;

/** Quién puede ver la disponibilidad de todos los productos (los @PreAuthorize, con la seguridad de métodos real). */
@SpringJUnitConfig(ProductoAdminControllerRolesTest.Config.class)
class ProductoAdminControllerRolesTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean
        ProductoAdminController productoAdminController() {
            return new ProductoAdminController(mock(ProductoService.class), mock(StockService.class));
        }
    }

    @Autowired
    private ProductoAdminController controller;

    private void pedir() {
        controller.disponibilidad(LocalDate.of(2099, 1, 1), LocalTime.of(18, 0), LocalTime.of(19, 30), null);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void elAdminPuede() {
        pedir();
        assertThat(controller).isNotNull();
    }

    @Test
    @WithMockUser(roles = "EMPLEADO")
    void elEmpleadoPuede() {
        pedir();
    }

    @Test
    @WithMockUser(roles = "CLIENTE")
    void elClienteNo() {
        assertThatThrownBy(this::pedir).isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test
    @WithMockUser(roles = "PROFESOR")
    void elProfesorNo() {
        assertThatThrownBy(this::pedir).isInstanceOf(AuthorizationDeniedException.class);
    }
}
