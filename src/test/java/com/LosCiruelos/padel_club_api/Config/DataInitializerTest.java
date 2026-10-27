package com.LosCiruelos.padel_club_api.Config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Categoria;
import com.LosCiruelos.padel_club_api.Entities.Enum.Posicion;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.club.service.CanchaService;
import com.LosCiruelos.padel_club_api.Services.ClienteProfileService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;

class DataInitializerTest {

    private UsuarioService usuarioService;
    private CanchaService canchaService;
    private ClienteProfileService clienteProfileService;
    private ConfiguracionRepository configuracionRepository;
    private DataInitializer initializer;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        canchaService = mock(CanchaService.class);
        clienteProfileService = mock(ClienteProfileService.class);
        configuracionRepository = mock(ConfiguracionRepository.class);
        initializer = new DataInitializer(usuarioService, canchaService, clienteProfileService, configuracionRepository);
        ReflectionTestUtils.setField(initializer, "adminEmail", "admin@test.com");
        ReflectionTestUtils.setField(initializer, "adminPassword", "Admin123!");
    }

    @Test
    void enUnaBaseVaciaCreaAdminConfiguracionYCanchas() {
        when(configuracionRepository.findById(1L)).thenReturn(Optional.empty());
        when(canchaService.count()).thenReturn(0L);

        initializer.run(null);

        verify(usuarioService).crearUsuario("admin@test.com", "Admin", "Los Ciruelos", null, "Admin123!",
                Role.ADMIN, AuthProvider.LOCAL, true, true, true);
        verify(configuracionRepository).save(any());
        verify(canchaService, times(4)).crearCancha(any(), anyString());
        // sin la propiedad, el usuario de prueba no se crea
        verify(usuarioService, never()).crearUsuario(eq("user.prueba@losciruelos.com"), any(), any(), any(), any(),
                any(), any(), any(), any(), any());
    }

    @Test
    void elUsuarioDePruebaSeCreaConSuPerfilSoloSiSeHabilita() {
        ReflectionTestUtils.setField(initializer, "crearUsuarioPrueba", true);
        when(usuarioService.findByEmail("admin@test.com")).thenReturn(TestData.usuario(1L, Role.ADMIN));
        when(configuracionRepository.findById(1L)).thenReturn(Optional.of(TestData.configuracion()));
        when(canchaService.count()).thenReturn(4L);
        Usuario prueba = TestData.usuario(2L, Role.CLIENTE);
        when(usuarioService.crearUsuario(eq("user.prueba@losciruelos.com"), any(), any(), any(), any(), any(), any(),
                any(), any(), any())).thenReturn(prueba);

        initializer.run(null);

        verify(clienteProfileService).crearClienteProfile(prueba, Categoria.Cuarta, Posicion.Drive);
        verify(configuracionRepository, never()).save(any());
        verify(canchaService, never()).crearCancha(any(), anyString());
    }
}
