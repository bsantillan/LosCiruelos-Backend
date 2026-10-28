package com.LosCiruelos.padel_club_api.usuarios.service;

import com.LosCiruelos.padel_club_api.usuarios.repository.UsuarioRepository;
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
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.VerificationToken;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.TokenType;
import com.LosCiruelos.padel_club_api.Exceptions.CodigoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.usuarios.entity.EnvioCodigoTelefono;
import com.LosCiruelos.padel_club_api.Exceptions.LimiteExcedidoException;
import com.LosCiruelos.padel_club_api.usuarios.repository.EnvioCodigoTelefonoRepository;
import com.LosCiruelos.padel_club_api.usuarios.repository.VerificationTokenRepository;
import com.LosCiruelos.padel_club_api.Services.WhatsApp.WhatsAppService;
import com.LosCiruelos.padel_club_api.Services.WhatsApp.WhatsAppServiceFactory;

class TelefonoServiceTest {

    private VerificationTokenRepository tokenRepository;
    private UsuarioService usuarioService;
    private UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    private WhatsAppService whatsApp;
    private TelefonoService telefonos;
    private EnvioCodigoTelefonoRepository envios;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        tokenRepository = mock(VerificationTokenRepository.class);
        usuarioService = mock(UsuarioService.class);
        whatsApp = mock(WhatsAppService.class);
        WhatsAppServiceFactory fabricaWhatsApp = mock(WhatsAppServiceFactory.class);
        when(fabricaWhatsApp.getService()).thenReturn(whatsApp);
        when(whatsApp.estaConfigurado()).thenReturn(true);
        when(whatsApp.enviarCodigo(anyString(), anyString(), anyString())).thenReturn(true);
        when(tokenRepository.save(any(VerificationToken.class))).thenAnswer(inv -> inv.getArgument(0));
        envios = mock(EnvioCodigoTelefonoRepository.class);
        when(envios.findFirstByUsuarioIdOrderByEnviadoAtDesc(any())).thenReturn(Optional.empty());
        telefonos = new TelefonoService(tokenRepository, envios, usuarioService, usuarioRepository, fabricaWhatsApp,
                TestData.reloj(AHORA), "codigo_verificacion_v2", 5, 5, 60);

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
                .isEqualTo(TestData.reloj(AHORA).instante().plus(TelefonoService.VIGENCIA_CODIGO));
        // sin transacción activa se envía en el acto
        verify(whatsApp).enviarCodigo("5491122334455", "codigo_verificacion_v2", token.getValue().getToken());
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
    void registraCadaEnvioParaLosTopes() {
        telefonos.enviarCodigo(usuario);

        ArgumentCaptor<EnvioCodigoTelefono> envio = ArgumentCaptor.forClass(EnvioCodigoTelefono.class);
        verify(envios).save(envio.capture());
        assertThat(envio.getValue().getUsuarioId()).isEqualTo(1L);
        assertThat(envio.getValue().getTelefono()).isEqualTo("5491122334455");
        assertThat(envio.getValue().getEnviadoAt()).isEqualTo(AHORA);
    }

    @Test
    void hayQueEsperarUnMinutoEntrePedidos() {
        when(envios.findFirstByUsuarioIdOrderByEnviadoAtDesc(1L)).thenReturn(Optional.of(
                EnvioCodigoTelefono.builder().usuarioId(1L).telefono("5491122334455")
                        .enviadoAt(AHORA.minusSeconds(20)).build()));

        assertThatThrownBy(() -> telefonos.enviarCodigo(usuario))
                .isInstanceOf(LimiteExcedidoException.class)
                .hasMessage("Esperá 40 segundos para pedir otro código.");
        verify(whatsApp, never()).enviarCodigo(anyString(), anyString(), anyString());
    }

    @Test
    void comoMaximo5CodigosPorDiaPorUsuario() {
        when(envios.countByUsuarioIdAndEnviadoAtAfter(1L, AHORA.minusHours(24))).thenReturn(5L);
        when(envios.primeroDelUsuarioDesde(1L, AHORA.minusHours(24))).thenReturn(Optional.of(AHORA.minusHours(21)));

        assertThatThrownBy(() -> telefonos.enviarCodigo(usuario))
                .isInstanceOf(LimiteExcedidoException.class)
                .hasMessageContaining("máximo de 5 códigos por día")
                .hasMessageContaining("en 3 h");
        verify(envios, never()).save(any());
    }

    @Test
    void comoMaximo5CodigosPorDiaAlMismoNumeroEntreTodasLasCuentas() {
        when(envios.countByTelefonoAndEnviadoAtAfter("5491122334455", AHORA.minusHours(24))).thenReturn(5L);
        when(envios.primeroDelNumeroDesde("5491122334455", AHORA.minusHours(24)))
                .thenReturn(Optional.of(AHORA.minusHours(23).minusMinutes(50)));

        assertThatThrownBy(() -> telefonos.enviarCodigo(usuario))
                .isInstanceOf(LimiteExcedidoException.class)
                .hasMessageContaining("Ese número ya recibió el máximo")
                .hasMessageContaining("en 10 minutos");
    }

    @Test
    void alElegirWhatsappConElTopeAlcanzadoNoFallaNiManda() {
        when(envios.countByUsuarioIdAndEnviadoAtAfter(1L, AHORA.minusHours(24))).thenReturn(5L);

        telefonos.enviarCodigoSiCorresponde(usuario);

        verify(whatsApp, never()).enviarCodigo(anyString(), anyString(), anyString());
    }

    @Test
    void noSeMandaElCodigoSiOtraCuentaActivaYaVerificoEseCelular() {
        when(usuarioService.telefonoVerificadoEnOtraCuenta("5491122334455", 1L)).thenReturn(true);

        assertThatThrownBy(() -> telefonos.enviarCodigo(usuario))
                .isInstanceOf(EstadoInvalidoException.class)
                .hasMessageContaining("otra cuenta");
        verify(whatsApp, never()).enviarCodigo(anyString(), anyString(), anyString());
    }

    @Test
    void siOtraCuentaLoVerificoMientrasTantoNoSeVerifica() {
        VerificationToken token = VerificationToken.builder().usuario(usuario).token("123456")
                .type(TokenType.VERIFY_PHONE).fechaExpiracion(java.time.Instant.now().plusSeconds(600)).build();
        when(tokenRepository.findByUsuarioAndType(usuario, TokenType.VERIFY_PHONE)).thenReturn(Optional.of(token));
        when(usuarioService.telefonoVerificadoEnOtraCuenta("5491122334455", 1L)).thenReturn(true);

        assertThatThrownBy(() -> telefonos.verificar(usuario, "123456")).isInstanceOf(EstadoInvalidoException.class);
        assertThat(usuario.getTelefonoVerificado()).isFalse();
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
        // las cuentas desactivadas que lo tenían verificado lo pierden
        verify(usuarioService).quitarVerificacionDeOtrasCuentas(usuario);
        verify(usuarioRepository).save(usuario);
        verify(tokenRepository).delete(token);
    }

    @Test
    void cambiarElTelefonoLoDejaSinVerificar() {
        UsuarioService real = new UsuarioService(mock(com.LosCiruelos.padel_club_api.usuarios.repository.UsuarioRepository.class),
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
