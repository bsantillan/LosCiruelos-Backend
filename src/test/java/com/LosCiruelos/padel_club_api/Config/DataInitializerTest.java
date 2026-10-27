package com.LosCiruelos.padel_club_api.Config;

import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Categoria;
import com.LosCiruelos.padel_club_api.Entities.Enum.Posicion;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.club.service.CanchaService;
import com.LosCiruelos.padel_club_api.Services.ClienteProfileService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;

class DataInitializerTest {

    private UsuarioService usuarioService;
    private CanchaService canchaService;
    private ClienteProfileService clienteProfileService;
    private ConfiguracionService configuracionService;
    private DataInitializer initializer;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        canchaService = mock(CanchaService.class);
        clienteProfileService = mock(ClienteProfileService.class);
        configuracionService = mock(ConfiguracionService.class);
        initializer = new DataInitializer(usuarioService, canchaService, clienteProfileService, configuracionService);
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@test.com");
        ReflectionTestUtils.setField(initializer, "adminPassword", "Admin123!");
    }

    @Test
    void enUnaBaseVaciaCreaAdminConfiguracionYCanchas() {
        when(configuracionService.crearSiNoExiste(any())).thenReturn(true);
        when(canchaService.crearCanchasSiNoHay(4, "Cancha de césped sintético con iluminación LED.")).thenReturn(true);

        initializer.run(null);

        verify(usuarioService).crearUsuario("admin@test.com", "Admin", "Los Ciruelos", null, "Admin123!",
                Role.ADMIN, AuthProvider.LOCAL, true, true, true);
        ArgumentCaptor<Configuracion> inicial = ArgumentCaptor.forClass(Configuracion.class);
        verify(configuracionService).crearSiNoExiste(inicial.capture());
        assertThat(inicial.getValue().getDias_apertura()).hasSize(7);
        assertThat(inicial.getValue().getPorcentajeSenia()).isEqualByComparingTo("50");
        verify(canchaService).crearCanchasSiNoHay(4, "Cancha de césped sintético con iluminación LED.");
        // sin la propiedad, el usuario de prueba no se crea
        verify(usuarioService, never()).crearUsuario(eq("user.prueba@losciruelos.com"), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void elUsuarioDePruebaSeCreaConSuPerfilSoloSiSeHabilita() {
        ReflectionTestUtils.setField(initializer, "crearUsuarioPrueba", true);
        when(usuarioService.findByEmail("admin@test.com")).thenReturn(TestData.usuario(1L, Role.ADMIN));
        Usuario prueba = TestData.usuario(2L, Role.CLIENTE);
        when(usuarioService.crearUsuario(eq("user.prueba@losciruelos.com"), any(), any(), any(), any(), any(), any(),
                any(), any(), any())).thenReturn(prueba);

        initializer.run(null);

        verify(clienteProfileService).crearClienteProfile(prueba, Categoria.Cuarta, Posicion.Drive);
        // la configuración y las canchas ya existen: los services no crean nada
        verify(configuracionService).crearSiNoExiste(any());
        verify(canchaService).crearCanchasSiNoHay(anyInt(), anyString());
    }
}
