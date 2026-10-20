package com.LosCiruelos.padel_club_api.Services;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.VerificationToken;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Entities.Enum.TokenType;
import com.LosCiruelos.padel_club_api.Exceptions.CodigoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.VerificationTokenRepository;
import com.LosCiruelos.padel_club_api.Services.WhatsApp.WhatsAppClient;

class TelefonoServiceTest {

    private VerificationTokenRepository tokenRepository;
    private UsuarioService usuarioService;
    private WhatsAppClient whatsApp;
    private TelefonoService telefonos;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(VerificationTokenRepository.class);
        usuarioService = mock(UsuarioService.class);
        whatsApp = mock(WhatsAppClient.class);
        when(whatsApp.estaConfigurado()).thenReturn(true);
        when(whatsApp.enviarCodigo(anyString(), anyString(), anyString())).thenReturn(true);
        when(tokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));
        telefonos = new TelefonoService(tokenRepository, usuarioService, whatsApp, TestData.reloj(AHORA),
                "codigo_verificacion");

        usuario = usuario(1L, Role.CLIENTE);
        usuario.setTelefono("5491122334455");
        usuario.setTelefonoVerificado(false);
    }

    @Test
    void mandaUnCodigoDe6DigitosQueVenceEn10Minutos() {
        telefonos.enviarCodigo(usuario);

        ArgumentCaptor<VerificationToken> token = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository).deleteByUsuarioAndType(usuario, TokenType.VERIFY_PHONE);
        verify(tokenRepository).save(token.capture());
        assertThat(token.getValue().getToken()).matches("[0-9]{6}");
        assertThat(token.getValue().getType()).isEqualTo(TokenType.VERIFY_PHONE);
        assertThat(token.getValue().getFechaExpiracion())
                .isEqualTo(TestData.reloj(AHORA).instant().plus(TelefonoService.VIGENCIA_CODIGO));
        // sin transacción activa se envía en el acto
        verify(whatsApp).enviarCodigo("5491122334455", "codigo_verificacion", token.getValue().getToken());
    }

    @Test
    void sinTelefonoYaVerificadoOSinWhatsappNoSeManda() {
        usuario.setTelefono(null);
        assertThatThrownBy(() -> telefonos.enviarCodigo(usuario)).isInstanceOf(ReservaException.class);

        usuario.setTelefono("5491122334455");
        usuario.setTelefonoVerificado(true);
        assertThatThrownBy(() -> telefonos.enviarCodigo(usuario)).isInstanceOf(EstadoInvalidoException.class);

        usuario.setTelefonoVerificado(false);
        when(whatsApp.estaConfigurado()).thenReturn(false);
        assertThatThrownBy(() -> telefonos.enviarCodigo(usuario))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("no están disponibles");

        // en el registro no se corta nada
        telefonos.enviarCodigoSiCorresponde(usuario);
        telefonos.enviarCodigoSiCorresponde(null);
        verify(whatsApp, never()).enviarCodigo(anyString(), anyString(), anyString());
    }

    @Test
    void elCodigoCorrectoVerificaElTelefono() {
        VerificationToken token = VerificationToken.builder().usuario(usuario).token("123456")
                .type(TokenType.VERIFY_PHONE).fechaExpiracion(java.time.Instant.now().plusSeconds(600)).build();
        when(tokenRepository.findByUsuarioAndType(usuario, TokenType.VERIFY_PHONE)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> telefonos.verificar(usuario, "000000")).isInstanceOf(CodigoInvalidoException.class);
        assertThat(token.getIntentos()).isEqualTo(1);
        assertThat(usuario.getTelefonoVerificado()).isFalse();

        telefonos.verificar(usuario, "123456");

        assertThat(usuario.getTelefonoVerificado()).isTrue();
        verify(usuarioService).save(usuario);
        verify(tokenRepository).delete(token);
    }

    @Test
    void cambiarElTelefonoLoDejaSinVerificar() {
        UsuarioService real = new UsuarioService(mock(com.LosCiruelos.padel_club_api.Repository.UsuarioRepository.class),
                mock(org.springframework.security.crypto.password.PasswordEncoder.class));
        usuario.setTelefonoVerificado(true);

        real.cambiarTelefono(usuario, "+54 9 11 2233-4455"); // el mismo número escrito distinto
        assertThat(usuario.getTelefonoVerificado()).isTrue();

        real.cambiarTelefono(usuario, "11 6123-4567");
        assertThat(usuario.getTelefono()).isEqualTo("5491161234567");
        assertThat(usuario.getTelefonoVerificado()).isFalse();
    }

    @Test
    void unCodigoDeOtroTipoNoSirve() {
        when(tokenRepository.findByUsuarioAndType(eq(usuario), eq(TokenType.VERIFY_PHONE))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> telefonos.verificar(usuario, "123456")).isInstanceOf(CodigoInvalidoException.class);
    }
}
