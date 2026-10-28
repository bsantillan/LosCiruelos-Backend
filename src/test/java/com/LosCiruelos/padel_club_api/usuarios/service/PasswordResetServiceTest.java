package com.LosCiruelos.padel_club_api.usuarios.service;

import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.usuarios.repository.UsuarioRepository;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.VerificationToken;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.TokenType;
import com.LosCiruelos.padel_club_api.Exceptions.CodigoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.Exceptions.PasswordInvalidaException;
import com.LosCiruelos.padel_club_api.usuarios.repository.VerificationTokenRepository;
import com.LosCiruelos.padel_club_api.Security.JWTUtil;
import com.LosCiruelos.padel_club_api.notificaciones.email.EmailService;
import com.LosCiruelos.padel_club_api.notificaciones.email.EmailServiceFactory;

class PasswordResetServiceTest {

    private UsuarioService usuarioService;
    private VerificationTokenRepository tokenRepository;
    private PasswordEncoder passwordEncoder;
    private EmailService emailService;
    private JWTUtil jwtUtil;
    private PasswordResetService servicio;
    private Usuario usuario;
    private VerificationToken token;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        tokenRepository = mock(VerificationTokenRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        emailService = mock(EmailService.class);
        jwtUtil = mock(JWTUtil.class);
        EmailServiceFactory factory = mock(EmailServiceFactory.class);
        when(factory.getService()).thenReturn(emailService);
        servicio = new PasswordResetService(usuarioService, mock(UsuarioRepository.class), tokenRepository, passwordEncoder, factory, jwtUtil,
                Reloj.delSistema());

        usuario = usuario(1L, Role.CLIENTE);
        token = VerificationToken.builder().usuario(usuario).token("654321").type(TokenType.PASSWORD_RESET)
                .fechaExpiracion(Instant.now().plus(15, ChronoUnit.MINUTES)).build();
        when(usuarioService.findByEmail(usuario.getEmail())).thenReturn(usuario);
        when(usuarioService.autenticado(usuario.getEmail())).thenReturn(usuario);
        when(tokenRepository.findByUsuarioAndType(usuario, TokenType.PASSWORD_RESET)).thenReturn(Optional.of(token));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(passwordEncoder.encode(anyString())).thenReturn("nuevo-hash");
    }

    @Test
    void enviaElCodigoSoloSiElEmailExiste() {
        servicio.enviarToken(usuario.getEmail());
        verify(tokenRepository).deleteByUsuarioAndType(usuario, TokenType.PASSWORD_RESET);
        verify(emailService).sendResetPasswordEmail(eq(usuario.getEmail()), anyMap());

        servicio.enviarToken("nadie@test.com");
        verify(emailService, never()).sendResetPasswordEmail(eq("nadie@test.com"), anyMap());
    }

    @Test
    void flujoCompletoCambiaLaClaveYCierraLasSesiones() {
        servicio.verificarToken(usuario.getEmail(), "654321");
        assertThat(token.getVerificado()).isTrue();

        servicio.resetPassword(usuario.getEmail(), "NuevaClave1!");

        assertThat(usuario.getPasswordHash()).isEqualTo("nuevo-hash");
        verify(tokenRepository).delete(token);
        verify(jwtUtil).deleteAllRefreshTokens(usuario);
    }

    @Test
    void sinVerificarElCodigoNoSeCambiaLaClave() {
        assertThatThrownBy(() -> servicio.resetPassword(usuario.getEmail(), "NuevaClave1!"))
                .isInstanceOf(CredencialesInvalidasException.class);
        assertThatThrownBy(() -> servicio.resetPassword(usuario.getEmail(), "debil"))
                .isInstanceOf(PasswordInvalidaException.class);
    }

    @Test
    void codigoVerificadoPeroVencidoNoSirve() {
        token.setVerificado(true);
        token.setFechaExpiracion(Instant.now().minusSeconds(1));

        assertThatThrownBy(() -> servicio.resetPassword(usuario.getEmail(), "NuevaClave1!"))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void codigoIncorrectoSumaUnIntento() {
        assertThatThrownBy(() -> servicio.verificarToken(usuario.getEmail(), "000000"))
                .isInstanceOf(CodigoInvalidoException.class);
        assertThat(token.getIntentos()).isEqualTo(1);
    }
}
