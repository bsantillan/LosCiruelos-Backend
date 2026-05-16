package com.LosCiruelos.padel_club_api.Services;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.LosCiruelos.padel_club_api.DTOs.Requests.LoginRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.RegisterRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.LoginResponse;
import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.Entities.RefreshToken;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Categoria;
import com.LosCiruelos.padel_club_api.Entities.Enum.Posicion;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.Exceptions.EmailEnUsoException;
import com.LosCiruelos.padel_club_api.Exceptions.EmailNoVerificadoException;
import com.LosCiruelos.padel_club_api.Exceptions.PasswordInvalidaException;
import com.LosCiruelos.padel_club_api.Exceptions.TerminosNoAceptadosException;
import com.LosCiruelos.padel_club_api.Security.GoogleTokenVerifier;
import com.LosCiruelos.padel_club_api.Security.JWTUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioService usuarioService;
    @Mock
    private ClienteProfileService clienteProfileService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JWTUtil jwtUtil;
    @Mock
    private GoogleTokenVerifier googleTokenVerifier;
    @Mock
    private VerificationService verificationService;

    @InjectMocks
    private AuthService authService;

    private Usuario usuarioMock;
    private ClienteProfile perfilMock;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach
    void setUp() {
        usuarioMock = Usuario.builder()
                .id(1L)
                .email("bruno@gmail.com")
                .nombre("Bruno")
                .apellido("Santillan")
                .telefono("1234567890")
                .passwordHash("hashed_password")
                .emailVerificado(true)
                .enabled(true)
                .rol(Role.CLIENTE)
                .provider(AuthProvider.LOCAL)
                .build();

        perfilMock = ClienteProfile.builder()
                .id(1L)
                .usuario(usuarioMock)
                .categoria(Categoria.Principiante)
                .posicion(Posicion.Ambos)
                .build();

        registerRequest = new RegisterRequest();
        registerRequest.setEmail("nuevo@gmail.com");
        registerRequest.setNombre("Juan");
        registerRequest.setApellido("Perez");
        registerRequest.setPassword("Password123!");
        registerRequest.setTelefono("1234567890");
        registerRequest.setTermsAccepted(true);
        registerRequest.setCategoria(Categoria.Primera);
        registerRequest.setPosicion(Posicion.Drive);

        loginRequest = new LoginRequest();
        loginRequest.setEmail("bruno@gmail.com");
        loginRequest.setPassword("Password123!");
    }

    // ========================
    // REGISTER
    // ========================

    @Test
    void register_exitoso() {
        when(usuarioService.findByEmail("nuevo@gmail.com")).thenReturn(null);
        when(usuarioService.crearUsuario(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usuarioMock);

        assertDoesNotThrow(() -> authService.register(registerRequest));

        verify(usuarioService).crearUsuario(any(), any(), any(), any(), any(), any(), any(), any());
        verify(clienteProfileService).crearClienteProfile(any(), any(), any());
        verify(verificationService).enviarToken(any());
    }

    @Test
    void register_emailEnUso_lanzaExcepcion() {
        when(usuarioService.findByEmail("nuevo@gmail.com")).thenReturn(usuarioMock);

        assertThrows(EmailEnUsoException.class, () -> authService.register(registerRequest));

        verify(usuarioService, never()).crearUsuario(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void register_passwordInvalida_lanzaExcepcion() {
        when(usuarioService.findByEmail("nuevo@gmail.com")).thenReturn(null);
        registerRequest.setPassword("123"); // password inválida

        assertThrows(PasswordInvalidaException.class, () -> authService.register(registerRequest));
    }

    @Test
    void register_terminosNoAceptados_lanzaExcepcion() {
        when(usuarioService.findByEmail("nuevo@gmail.com")).thenReturn(null);
        registerRequest.setTermsAccepted(false);

        assertThrows(TerminosNoAceptadosException.class, () -> authService.register(registerRequest));
    }

    @Test
    void register_fallaMail_noLanzaExcepcion() {
        when(usuarioService.findByEmail("nuevo@gmail.com")).thenReturn(null);
        when(usuarioService.crearUsuario(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(usuarioMock);
        doThrow(new RuntimeException("SMTP error")).when(verificationService).enviarToken(any());

        // El registro debe ser exitoso aunque el mail falle
        assertDoesNotThrow(() -> authService.register(registerRequest));
    }

    // ========================
    // LOGIN
    // ========================

    @Test
    void login_exitoso() {
        when(usuarioService.findByEmailOrThrow(eq("bruno@gmail.com"), any()))
                .thenReturn(usuarioMock);
        when(passwordEncoder.matches("Password123!", "hashed_password")).thenReturn(true);
        when(jwtUtil.generateToken(any())).thenReturn("access_token");
        when(jwtUtil.createRefreshToken(any())).thenReturn(
                RefreshToken.builder().token("refresh_token").usuario(usuarioMock)
                        .fechaExpiracion(Instant.now().plusSeconds(604800)).build());

        LoginResponse response = authService.login(loginRequest);

        assertNotNull(response);
        assertEquals("access_token", response.getAccessToken());
        assertEquals("refresh_token", response.getRefreshToken());
        assertEquals("bruno@gmail.com", response.getEmail());
    }

    @Test
    void login_passwordIncorrecta_lanzaExcepcion() {
        when(usuarioService.findByEmailOrThrow(eq("bruno@gmail.com"), any()))
                .thenReturn(usuarioMock);
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThrows(CredencialesInvalidasException.class, () -> authService.login(loginRequest));
    }

    @Test
    void login_emailNoVerificado_lanzaExcepcion() {
        usuarioMock.setEmailVerificado(false);
        when(usuarioService.findByEmailOrThrow(eq("bruno@gmail.com"), any()))
                .thenReturn(usuarioMock);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        assertThrows(EmailNoVerificadoException.class, () -> authService.login(loginRequest));
    }

    // ========================
    // LOGOUT
    // ========================

    @Test
    void logout_tokenInvalido_lanzaExcepcion() {
        when(jwtUtil.validateRefreshToken("token_invalido")).thenReturn(false);

        assertThrows(CredencialesInvalidasException.class,
                () -> authService.logout("token_invalido"));
    }

    // ========================
    // REFRESH TOKEN
    // ========================

    @Test
    void refresh_exitoso() {
        RefreshToken nuevoToken = RefreshToken.builder()
                .token("nuevo_refresh_token")
                .usuario(usuarioMock)
                .fechaExpiracion(Instant.now().plusSeconds(604800))
                .build();

        when(jwtUtil.validateRefreshToken("viejo_token")).thenReturn(true);
        when(jwtUtil.rotateRefreshToken("viejo_token")).thenReturn(nuevoToken);
        when(jwtUtil.generateToken(any())).thenReturn("nuevo_access_token");
        when(clienteProfileService.findByUsuario(usuarioMock)).thenReturn(perfilMock);

        LoginResponse response = authService.refreshToken("viejo_token");

        assertNotNull(response);
        assertEquals("nuevo_access_token", response.getAccessToken());
        assertEquals("nuevo_refresh_token", response.getRefreshToken());
    }

    @Test
    void refresh_tokenInvalido_lanzaExcepcion() {
        when(jwtUtil.validateRefreshToken("token_invalido")).thenReturn(false);

        assertThrows(CredencialesInvalidasException.class,
                () -> authService.refreshToken("token_invalido"));
    }
}