package com.LosCiruelos.padel_club_api.usuarios.service;

import com.LosCiruelos.padel_club_api.usuarios.repository.UsuarioRepository;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.LosCiruelos.padel_club_api.mapper.UsuarioMapper;
import com.LosCiruelos.padel_club_api.usuarios.dto.request.LoginRequest;
import com.LosCiruelos.padel_club_api.usuarios.dto.request.RegisterRequest;
import com.LosCiruelos.padel_club_api.security.GoogleUser;
import com.LosCiruelos.padel_club_api.usuarios.dto.response.LoginResponse;
import com.LosCiruelos.padel_club_api.usuarios.entity.ClienteProfile;
import com.LosCiruelos.padel_club_api.usuarios.entity.RefreshToken;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.AuthProvider;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Posicion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.exception.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.usuarios.exception.CuentaDesactivadaException;
import com.LosCiruelos.padel_club_api.usuarios.exception.EmailEnUsoException;
import com.LosCiruelos.padel_club_api.usuarios.exception.EmailNoVerificadoException;
import com.LosCiruelos.padel_club_api.usuarios.exception.PasswordInvalidaException;
import com.LosCiruelos.padel_club_api.usuarios.exception.TerminosNoAceptadosException;
import com.LosCiruelos.padel_club_api.security.GoogleTokenVerifier;
import com.LosCiruelos.padel_club_api.security.JWTUtil;
import com.LosCiruelos.padel_club_api.security.UsuarioPrincipal;

class AuthServiceTest {

    private UsuarioService usuarioService;
    private ClienteProfileService clienteProfileService;
    private PasswordEncoder passwordEncoder;
    private JWTUtil jwtUtil;
    private GoogleTokenVerifier googleTokenVerifier;
    private VerificationService verificationService;
    private AuthService authService;
    private UsuarioRepository usuarioRepository;
    private Usuario cliente;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        clienteProfileService = mock(ClienteProfileService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtUtil = mock(JWTUtil.class);
        googleTokenVerifier = mock(GoogleTokenVerifier.class);
        verificationService = mock(VerificationService.class);
        usuarioRepository = mock(UsuarioRepository.class);
        authService = new AuthService(usuarioService, usuarioRepository, clienteProfileService, passwordEncoder, jwtUtil,
                googleTokenVerifier, verificationService, new UsuarioMapper());

        cliente = usuario(1L, Role.CLIENTE);
        cliente.setPasswordHash("hash");
        when(jwtUtil.generateToken(anyString())).thenReturn("jwt");
        when(jwtUtil.createRefreshToken(any())).thenReturn(RefreshToken.builder().token("rt").build());
        when(passwordEncoder.matches("Clave123!", "hash")).thenReturn(true);
        when(usuarioRepository.findByEmail(cliente.getEmail())).thenReturn(java.util.Optional.of(cliente));
        // la regla por rol es la real; el perfil de jugador lo responde el mock (completo)
        when(clienteProfileService.findByUsuario(any())).thenAnswer(inv -> ClienteProfile.builder()
                .usuario(inv.getArgument(0)).categoria(Categoria.Cuarta).posicion(Posicion.Drive).build());
        when(clienteProfileService.perfilCompleto(any())).thenCallRealMethod();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private static LoginRequest login(String email, String password) {
        LoginRequest req = new LoginRequest();
        req.setEmail(email);
        req.setPassword(password);
        return req;
    }

    private static RegisterRequest registro(String password, Boolean terminos) {
        RegisterRequest req = new RegisterRequest();
        req.setEmail("nuevo@test.com");
        req.setNombre("Nuevo");
        req.setApellido("Usuario");
        req.setPassword(password);
        req.setTermsAccepted(terminos);
        req.setTelefono("1122334455");
        req.setCategoria(Categoria.Cuarta);
        req.setPosicion(Posicion.Drive);
        return req;
    }

    // ── registro ──

    @Test
    void elRegistroGuardaElConsentimientoYArrancaPorMail() {
        Usuario creado = usuario(9L, Role.CLIENTE);
        creado.setTelefonoVerificado(false);
        when(usuarioService.crearUsuario(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(creado);

        RegisterRequest req = registro("Clave123!", true);
        req.setAceptarNotificacion(true);
        authService.register(req);

        assertThat(creado.getNotificacionesAceptadasAt()).isNotNull();
        // el medio se elige en el perfil, después de validar el celular
        assertThat(creado.getCanalNotificaciones()).isEqualTo(CanalNotificacion.EMAIL);
        assertThat(creado.getAceptaWhatsappAt()).isNull();
    }

    @Test
    void registroCreaClienteHabilitadoConEmailSinVerificar() {
        Usuario creado = usuario(9L, Role.CLIENTE);
        when(usuarioService.crearUsuario(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(creado);

        authService.register(registro("Clave123!", true));

        verify(usuarioService).crearUsuario("nuevo@test.com", "Nuevo", "Usuario", "1122334455", "Clave123!",
                Role.CLIENTE, AuthProvider.LOCAL, true, false, true);
        verify(clienteProfileService).crearClienteProfile(creado, Categoria.Cuarta, Posicion.Drive);
        verify(verificationService).enviarToken(creado.getEmail());
    }

    @Test
    void registroValidaEmailContraseniaYTerminos() {
        when(usuarioService.findByEmail("nuevo@test.com")).thenReturn(cliente);
        assertThatThrownBy(() -> authService.register(registro("Clave123!", true))).isInstanceOf(EmailEnUsoException.class);

        when(usuarioService.findByEmail("nuevo@test.com")).thenReturn(null);
        assertThatThrownBy(() -> authService.register(registro("debil", true))).isInstanceOf(PasswordInvalidaException.class);
        assertThatThrownBy(() -> authService.register(registro("Clave123!", false)))
                .isInstanceOf(TerminosNoAceptadosException.class);
    }

    // ── login ──

    @Test
    void loginCorrectoDevuelveTokens() {
        LoginResponse r = authService.login(login(cliente.getEmail(), "Clave123!"));

        assertThat(r.getAccessToken()).isEqualTo("jwt");
        assertThat(r.getRefreshToken()).isEqualTo("rt");
        assertThat(r.getPerfilCompleto()).isTrue();
        verify(jwtUtil).deleteAllRefreshTokens(cliente);
    }

    @Test
    void loginRechazaClaveIncorrectaEmailSinVerificarYCuentaDesactivada() {
        assertThatThrownBy(() -> authService.login(login(cliente.getEmail(), "otra")))
                .isInstanceOf(CredencialesInvalidasException.class);

        cliente.setEmailVerificado(false);
        assertThatThrownBy(() -> authService.login(login(cliente.getEmail(), "Clave123!")))
                .isInstanceOf(EmailNoVerificadoException.class);

        cliente.setEmailVerificado(true);
        cliente.setEnabled(false);
        assertThatThrownBy(() -> authService.login(login(cliente.getEmail(), "Clave123!")))
                .isInstanceOf(CuentaDesactivadaException.class);
    }

    @Test
    void elPerfilDeUnEmpleadoSiempreEstaCompleto() {
        Usuario empleado = usuario(2L, Role.EMPLEADO);
        empleado.setPasswordHash("hash");
        when(usuarioRepository.findByEmail(empleado.getEmail())).thenReturn(java.util.Optional.of(empleado));
        when(clienteProfileService.findByUsuario(any())).thenReturn(null);

        assertThat(authService.login(login(empleado.getEmail(), "Clave123!")).getPerfilCompleto()).isTrue();
    }

    @Test
    void elAdminNecesitaElCelularVerificado() {
        Usuario admin = usuario(3L, Role.ADMIN);
        admin.setPasswordHash("hash");
        admin.setTelefonoVerificado(false);
        when(usuarioRepository.findByEmail(admin.getEmail())).thenReturn(java.util.Optional.of(admin));

        assertThat(authService.login(login(admin.getEmail(), "Clave123!")).getPerfilCompleto()).isFalse();

        admin.setTelefonoVerificado(true);
        assertThat(authService.login(login(admin.getEmail(), "Clave123!")).getPerfilCompleto()).isTrue();
    }

    // ── Google ──

    @Test
    void googleCreaUnClienteNuevoConPerfilIncompleto() {
        when(googleTokenVerifier.verify("id-token")).thenReturn(new GoogleUser("g@test.com", "Gabi", "Gomez"));
        Usuario creado = usuario(5L, Role.CLIENTE);
        when(usuarioService.crearUsuario(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(creado);
        // el cliente nuevo de Google todavía no tiene categoría ni posición
        when(clienteProfileService.findByUsuario(any())).thenReturn(null);

        LoginResponse r = authService.loginWithGoogle("id-token");

        verify(usuarioService).crearUsuario(eq("g@test.com"), eq("Gabi"), eq("Gomez"), isNull(), isNull(),
                eq(Role.CLIENTE), eq(AuthProvider.GOOGLE), eq(true), eq(true), eq(true));
        assertThat(r.getPerfilCompleto()).isFalse();
    }

    @Test
    void googleVerificaUnaCuentaLocalPendiente() {
        // antes devolvía "Cuenta desactivada" porque enabled=false significaba "no verificado"
        cliente.setEmailVerificado(false);
        when(googleTokenVerifier.verify("id-token")).thenReturn(new GoogleUser(cliente.getEmail(), "N", "A"));
        when(usuarioService.findByEmail(cliente.getEmail())).thenReturn(cliente);
        when(clienteProfileService.findByUsuario(cliente)).thenReturn(ClienteProfile.builder().usuario(cliente).build());

        authService.loginWithGoogle("id-token");

        assertThat(cliente.getEmailVerificado()).isTrue();
        verify(usuarioRepository).save(cliente);
    }

    @Test
    void googleNoDejaEntrarAUnaCuentaDesactivada() {
        cliente.setEnabled(false);
        when(googleTokenVerifier.verify("id-token")).thenReturn(new GoogleUser(cliente.getEmail(), "N", "A"));
        when(usuarioService.findByEmail(cliente.getEmail())).thenReturn(cliente);

        assertThatThrownBy(() -> authService.loginWithGoogle("id-token")).isInstanceOf(CuentaDesactivadaException.class);
    }

    // ── refresh / logout ──

    @Test
    void refreshRotaElToken() {
        when(jwtUtil.validateRefreshToken("viejo")).thenReturn(true);
        when(jwtUtil.getUsuarioFromToken("viejo")).thenReturn(cliente);
        when(jwtUtil.rotateRefreshToken("viejo")).thenReturn(RefreshToken.builder().token("nuevo").usuario(cliente).build());

        assertThat(authService.refreshToken("viejo").getRefreshToken()).isEqualTo("nuevo");
    }

    @Test
    void refreshDeUnaCuentaDesactivadaSeRechaza() {
        cliente.setEnabled(false);
        when(jwtUtil.validateRefreshToken("viejo")).thenReturn(true);
        when(jwtUtil.getUsuarioFromToken("viejo")).thenReturn(cliente);

        assertThatThrownBy(() -> authService.refreshToken("viejo")).isInstanceOf(CuentaDesactivadaException.class);
        verify(jwtUtil, never()).rotateRefreshToken(any());

        when(jwtUtil.validateRefreshToken("invalido")).thenReturn(false);
        assertThatThrownBy(() -> authService.refreshToken("invalido")).isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void logoutSoloConElRefreshTokenPropio() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(new UsuarioPrincipal(cliente), null));
        Usuario otro = usuario(2L, Role.CLIENTE);
        when(jwtUtil.validateRefreshToken(anyString())).thenReturn(true);
        when(jwtUtil.getUsuarioFromToken("ajeno")).thenReturn(otro);
        when(jwtUtil.getUsuarioFromToken("propio")).thenReturn(cliente);

        assertThatThrownBy(() -> authService.logout("ajeno")).isInstanceOf(CredencialesInvalidasException.class);
        authService.logout("propio");
        verify(jwtUtil).deleteRefreshToken("propio");
    }
}
