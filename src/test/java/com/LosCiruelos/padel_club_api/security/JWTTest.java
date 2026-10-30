package com.LosCiruelos.padel_club_api.security;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;

class JWTTest {

    private JWTUtil jwtUtil;

    private static JWTUtil crear(Reloj reloj, String secret) {
        JWTUtil util = new JWTUtil(reloj);
        ReflectionTestUtils.setField(util, "secret", secret);
        ReflectionTestUtils.setField(util, "expiration", 900L);
        ReflectionTestUtils.invokeMethod(util, "init");
        return util;
    }

    @BeforeEach
    void setUp() {
        jwtUtil = crear(TestData.reloj(AHORA), "0123456789abcdef0123456789abcdef");
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
    void elAccessTokenVenceALos15Minutos() {
        String token = jwtUtil.generateToken("a@b.com");
        String secret = "0123456789abcdef0123456789abcdef";

        assertThat(crear(TestData.reloj(AHORA.plusMinutes(14)), secret).validateAccessToken(token)).isTrue();
        assertThat(crear(TestData.reloj(AHORA.plusMinutes(16)), secret).validateAccessToken(token)).isFalse();
    }

    @Test
    void unTokenFirmadoConOtraClaveNoEsValido() {
        JWTUtil otro = crear(TestData.reloj(AHORA), "ffffffffffffffffffffffffffffffff");
        assertThat(jwtUtil.validateAccessToken(otro.generateToken("a@b.com"))).isFalse();
    }

    @Test
    void unaClaveCortaFallaAlArrancarConUnMensajeClaro() {
        assertThatThrownBy(() -> crear(TestData.reloj(AHORA), "corta"))
                .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("32 bytes"));
    }

    @Test
    void elFiltroAutenticaSoloUsuariosHabilitados() throws Exception {
        UsuarioService usuarioService = mock(UsuarioService.class);
        Usuario usuario = usuario(1L, Role.CLIENTE);
        when(usuarioService.findByEmail(usuario.getEmail())).thenReturn(usuario);
        JWTFilter filtro = new JWTFilter(jwtUtil, usuarioService);

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
