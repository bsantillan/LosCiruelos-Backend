package com.LosCiruelos.padel_club_api.usuarios.service;

import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.usuarios.repository.UsuarioRepository;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.VerificationToken;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.TokenType;
import com.LosCiruelos.padel_club_api.usuarios.exception.CodigoInvalidoException;
import com.LosCiruelos.padel_club_api.usuarios.exception.CuentaVerificadaException;
import com.LosCiruelos.padel_club_api.usuarios.repository.VerificationTokenRepository;
import com.LosCiruelos.padel_club_api.notificaciones.email.EmailService;
import com.LosCiruelos.padel_club_api.notificaciones.email.EmailServiceFactory;

class VerificationServiceTest {

    private UsuarioService usuarioService;
    private VerificationTokenRepository tokenRepository;
    private EmailService emailService;
    private VerificationService servicio;
    private Usuario usuario;
    private VerificationToken token;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        tokenRepository = mock(VerificationTokenRepository.class);
        emailService = mock(EmailService.class);
        EmailServiceFactory factory = mock(EmailServiceFactory.class);
        when(factory.getService()).thenReturn(emailService);
        servicio = new VerificationService(usuarioService, mock(UsuarioRepository.class), tokenRepository, factory,
                Reloj.delSistema());

        usuario = usuario(1L, Role.CLIENTE);
        usuario.setEmailVerificado(false);
        token = VerificationToken.builder().usuario(usuario).token("123456").type(TokenType.VERIFY_EMAIL)
                .fechaExpiracion(Instant.now().plus(1, ChronoUnit.HOURS)).build();

        when(usuarioService.autenticado(usuario.getEmail())).thenReturn(usuario);
        when(usuarioService.findByEmail(usuario.getEmail())).thenReturn(usuario);
        when(tokenRepository.findByUsuarioAndType(usuario, TokenType.VERIFY_EMAIL)).thenReturn(Optional.of(token));
        when(tokenRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void codigoCorrectoVerificaElEmailSinReactivarLaCuenta() {
        usuario.setEnabled(false); // desactivada por el admin

        servicio.verificarToken(usuario.getEmail(), "123456");

        assertThat(usuario.getEmailVerificado()).isTrue();
        assertThat(usuario.getEnabled()).isFalse();
        verify(tokenRepository).delete(token);
    }

    @Test
    void despuesDe5IntentosFallidosElCodigoQuedaBloqueado() {
        for (int i = 0; i < VerificationToken.MAX_INTENTOS; i++) {
            assertThatThrownBy(() -> servicio.verificarToken(usuario.getEmail(), "000000"))
                    .isInstanceOf(CodigoInvalidoException.class);
        }
        assertThat(token.getIntentos()).isEqualTo(5);

        // aun con el código correcto
        assertThatThrownBy(() -> servicio.verificarToken(usuario.getEmail(), "123456"))
                .isInstanceOf(CodigoInvalidoException.class)
                .hasMessageContaining("intentos");
        assertThat(usuario.getEmailVerificado()).isFalse();
    }

    @Test
    void codigoVencido() {
        token.setFechaExpiracion(Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> servicio.verificarToken(usuario.getEmail(), "123456"))
                .isInstanceOf(CodigoInvalidoException.class)
                .hasMessageContaining("expiró");
    }

    @Test
    void cuentaYaVerificada() {
        usuario.setEmailVerificado(true);
        assertThatThrownBy(() -> servicio.verificarToken(usuario.getEmail(), "123456"))
                .isInstanceOf(CuentaVerificadaException.class);
    }

    @Test
    void enviarCodigoBorraElAnteriorYMandaElEmail() {
        servicio.enviarToken(usuario.getEmail());

        verify(tokenRepository).deleteByUsuarioAndType(usuario, TokenType.VERIFY_EMAIL);
        verify(emailService).sendVerificationEmail(eq(usuario.getEmail()), anyMap());
    }

    @Test
    void losCodigosTienenSeisDigitos() {
        for (int i = 0; i < 100; i++) {
            assertThat(VerificationToken.generarCodigo()).matches("\\d{6}");
        }
    }
}
