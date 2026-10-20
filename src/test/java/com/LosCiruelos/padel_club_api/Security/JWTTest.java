package com.LosCiruelos.padel_club_api.Security;

import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.LosCiruelos.padel_club_api.Entities.RefreshToken;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.Repository.RefreshTokenRepository;
import com.LosCiruelos.padel_club_api.Repository.UsuarioRepository;

class JWTTest {

    private RefreshTokenRepository refreshTokenRepository;
    private JWTUtil jwtUtil;

    private static JWTUtil crear(RefreshTokenRepository repo, String secret) {
        JWTUtil util = new JWTUtil(repo);
        ReflectionTestUtils.setField(util, "secret", secret);
        ReflectionTestUtils.setField(util, "expiration", 900L);
        ReflectionTestUtils.setField(util, "expirationRefresh", 604800L);
        ReflectionTestUtils.invokeMethod(util, "init");
        return util;
    }

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        when(refreshTokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        jwtUtil = crear(refreshTokenRepository, "0123456789abcdef0123456789abcdef");
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void elAccessTokenLlevaElEmail() {
        String token = jwtUtil.generateToken("a@b.com");

        assertThat(jwtUtil.validateAccessToken(token)).isTrue();
        assertThat(jwtUtil.recuperarMail(token)).isEqualTo("a@b.com");
        assertThat(jwtUtil.validateAccessToken(token + "x")).isFalse();
        assertThat(jwtUtil.recuperarMail("basura")).isNull();
    }

    @Test
    void unTokenFirmadoConOtraClaveNoEsValido() {
        JWTUtil otro = crear(refreshTokenRepository, "ffffffffffffffffffffffffffffffff");
        assertThat(jwtUtil.validateAccessToken(otro.generateToken("a@b.com"))).isFalse();
    }

    @Test
    void unaClaveCortaFallaAlArrancarConUnMensajeClaro() {
        assertThatThrownBy(() -> crear(refreshTokenRepository, "corta"))
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("32 bytes"));
    }

    @Test
    void refreshTokens() {
        Usuario usuario = usuario(1L, Role.CLIENTE);
        RefreshToken creado = jwtUtil.createRefreshToken(usuario);
        assertThat(creado.getFechaExpiracion()).isAfter(Instant.now().plusSeconds(604000));

        when(refreshTokenRepository.findByToken(creado.getToken())).thenReturn(Optional.of(creado));
        assertThat(jwtUtil.validateRefreshToken(creado.getToken())).isTrue();
        assertThat(jwtUtil.getUsuarioFromToken(creado.getToken())).isEqualTo(usuario);
        assertThat(jwtUtil.rotateRefreshToken(creado.getToken()).getToken()).isNotEqualTo(creado.getToken());

        creado.setFechaExpiracion(Instant.now().minusSeconds(1));
        assertThat(jwtUtil.validateRefreshToken(creado.getToken())).isFalse();

        when(refreshTokenRepository.findByToken("nada")).thenReturn(Optional.empty());
        assertThat(jwtUtil.validateRefreshToken("nada")).isFalse();
        assertThatThrownBy(() -> jwtUtil.getUsuarioFromToken("nada")).isInstanceOf(CredencialesInvalidasException.class);
        assertThatThrownBy(() -> jwtUtil.rotateRefreshToken("nada")).isInstanceOf(CredencialesInvalidasException.class);

        jwtUtil.cleanExpiredTokens();
        verify(refreshTokenRepository).deleteAllByFechaExpiracionBefore(any());
    }

    @Test
    void elFiltroAutenticaSoloUsuariosHabilitados() throws Exception {
        UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
        Usuario usuario = usuario(1L, Role.CLIENTE);
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        JWTFilter filtro = new JWTFilter(jwtUtil, usuarioRepository);

        Authentication auth = filtrar(filtro, "Bearer " + jwtUtil.generateToken(usuario.getEmail()));
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_CLIENTE");

        SecurityContextHolder.clearContext();
        usuario.setEnabled(false);
        assertThat(filtrar(filtro, "Bearer " + jwtUtil.generateToken(usuario.getEmail()))).isNull();
        assertThat(filtrar(filtro, "Bearer invalido")).isNull();
        assertThat(filtrar(filtro, null)).isNull();
    }

    private static Authentication filtrar(JWTFilter filtro, String header) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/reservas/mias");
        if (header != null) {
            request.addHeader("Authorization", header);
        }
        filtro.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
