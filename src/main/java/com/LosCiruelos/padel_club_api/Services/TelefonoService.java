package com.LosCiruelos.padel_club_api.Services;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.VerificationToken;
import com.LosCiruelos.padel_club_api.Entities.Enum.TokenType;
import com.LosCiruelos.padel_club_api.Exceptions.CodigoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.VerificationTokenRepository;
import com.LosCiruelos.padel_club_api.Services.Email.EnvioPostCommit;
import com.LosCiruelos.padel_club_api.Services.WhatsApp.WhatsAppClient;

import lombok.extern.slf4j.Slf4j;

/**
 * Verificación del teléfono con un código de 6 dígitos que llega por WhatsApp (igual que el
 * código del mail). Ingresar el código prueba que el número existe, tiene WhatsApp y es del
 * usuario. Solo con el teléfono verificado se le mandan avisos por WhatsApp.
 */
@Slf4j
@Service
public class TelefonoService {

    static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(10);

    private final VerificationTokenRepository tokenRepository;
    private final UsuarioService usuarioService;
    private final WhatsAppClient whatsApp;
    private final Clock clock;
    private final String plantillaCodigo;

    public TelefonoService(VerificationTokenRepository tokenRepository, UsuarioService usuarioService,
            WhatsAppClient whatsApp, Clock clock,
            @Value("${whatsapp.plantilla-codigo:codigo_verificacion}") String plantillaCodigo) {
        this.tokenRepository = tokenRepository;
        this.usuarioService = usuarioService;
        this.whatsApp = whatsApp;
        this.clock = clock;
        this.plantillaCodigo = plantillaCodigo;
    }

    /** Genera un código y lo manda por WhatsApp al teléfono del usuario (después del commit). */
    @Transactional
    public void enviarCodigo(Usuario usuario) {
        if (usuario.getTelefono() == null) {
            throw new ReservaException("Cargá tu teléfono antes de verificarlo.");
        }
        if (Boolean.TRUE.equals(usuario.getTelefonoVerificado())) {
            throw new EstadoInvalidoException("Tu teléfono ya está verificado.");
        }
        if (!whatsApp.estaConfigurado()) {
            throw new EstadoInvalidoException("Los avisos por WhatsApp no están disponibles por ahora.");
        }

        tokenRepository.deleteByUsuarioAndType(usuario, TokenType.VERIFY_PHONE);
        VerificationToken token = tokenRepository.save(VerificationToken.builder()
                .usuario(usuario)
                .token(VerificationToken.generarCodigo())
                .type(TokenType.VERIFY_PHONE)
                .fechaExpiracion(clock.instant().plus(VIGENCIA_CODIGO))
                .build());

        String telefono = usuario.getTelefono();
        String codigo = token.getToken();
        EnvioPostCommit.ejecutar("código de WhatsApp a " + telefono, () -> {
            if (!whatsApp.enviarCodigo(telefono, plantillaCodigo, codigo)) {
                log.warn("No llegó a enviarse el código de verificación a {}", telefono);
            }
        });
    }

    /** Igual que {@link #enviarCodigo} pero sin errores: para el registro y para reenvíos públicos. */
    @Transactional
    public void enviarCodigoSiCorresponde(Usuario usuario) {
        if (usuario != null && usuario.getTelefono() != null && !Boolean.TRUE.equals(usuario.getTelefonoVerificado())
                && whatsApp.estaConfigurado()) {
            enviarCodigo(usuario);
        }
    }

    /** Los intentos fallidos se guardan aunque falle (5 intentos, como el código del mail). */
    @Transactional(noRollbackFor = CodigoInvalidoException.class)
    public void verificar(Usuario usuario, String codigo) {
        if (Boolean.TRUE.equals(usuario.getTelefonoVerificado())) {
            return;
        }
        VerificationToken token = VerificationService.validarCodigo(tokenRepository, usuario,
                TokenType.VERIFY_PHONE, codigo);
        usuario.setTelefonoVerificado(true);
        usuarioService.save(usuario);
        tokenRepository.delete(token);
        log.info("Teléfono {} verificado para {}", usuario.getTelefono(), usuario.getEmail());
    }
}
