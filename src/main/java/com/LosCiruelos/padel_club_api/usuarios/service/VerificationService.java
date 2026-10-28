package com.LosCiruelos.padel_club_api.usuarios.service;

import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.usuarios.repository.UsuarioRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.VerificationToken;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.TokenType;
import com.LosCiruelos.padel_club_api.Exceptions.CodigoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.CuentaVerificadaException;
import com.LosCiruelos.padel_club_api.usuarios.repository.VerificationTokenRepository;
import com.LosCiruelos.padel_club_api.Services.Email.EmailServiceFactory;
import com.LosCiruelos.padel_club_api.Services.Email.EnvioPostCommit;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final VerificationTokenRepository tokenRepository;
    private final EmailServiceFactory emailServiceFactory;
    private final Reloj reloj;

    @Transactional
    public void enviarToken(String email) {
        Usuario usuario = usuarioService.findByEmail(email);
        if (usuario == null || usuario.getEmailVerificado()) {
            return;
        }

        VerificationToken token = crearToken(usuario, TokenType.VERIFY_EMAIL, reloj.instante().plus(1, ChronoUnit.DAYS));
        Map<String, Object> datos = Map.of("nombre", nombre(usuario), "codigo", token.getToken());
        EnvioPostCommit.ejecutar("verificación a " + usuario.getEmail(),
                () -> emailServiceFactory.getService().sendVerificationEmail(usuario.getEmail(), datos));
    }

    /**
     * Los intentos fallidos se guardan aunque se lance la excepción (noRollbackFor);
     * con 5 fallos el código queda inutilizable y hay que pedir otro.
     */
    @Transactional(noRollbackFor = CodigoInvalidoException.class)
    public void verificarToken(String email, String codigo) {
        Usuario usuario = usuarioService.autenticado(email);

        if (usuario.getEmailVerificado()) {
            throw new CuentaVerificadaException();
        }

        VerificationToken token = validarCodigo(tokenRepository, usuario, TokenType.VERIFY_EMAIL, codigo, reloj.instante());

        // Solo marca el email como verificado: "enabled" es para cuentas desactivadas
        usuario.setEmailVerificado(true);
        usuarioRepository.save(usuario);
        tokenRepository.delete(token);
    }

    @Transactional
    public void enviarBienvenida(String email, String passwordTemporal) {
        Usuario usuario = usuarioService.autenticado(email);

        VerificationToken token = crearToken(usuario, TokenType.VERIFY_EMAIL, reloj.instante().plus(1, ChronoUnit.DAYS));
        Map<String, Object> datos = Map.of(
                "nombre", nombre(usuario),
                "password", passwordTemporal,
                "codigo", token.getToken());
        EnvioPostCommit.ejecutar("bienvenida a " + usuario.getEmail(),
                () -> emailServiceFactory.getService().sendBienvenidaEmail(usuario.getEmail(), datos));
    }

    private VerificationToken crearToken(Usuario usuario, TokenType tipo, Instant expiracion) {
        tokenRepository.deleteByUsuarioAndType(usuario, tipo);
        return tokenRepository.save(VerificationToken.builder()
                .usuario(usuario)
                .token(VerificationToken.generarCodigo())
                .type(tipo)
                .fechaExpiracion(expiracion)
                .build());
    }

    /** Valida el código del usuario; si no coincide suma un intento fallido. */
    static VerificationToken validarCodigo(VerificationTokenRepository repo, Usuario usuario, TokenType tipo,
            String codigo, Instant ahora) {
        VerificationToken token = repo.findByUsuarioAndType(usuario, tipo)
                .orElseThrow(() -> new CodigoInvalidoException("Código inválido"));

        if (token.esExpirado(ahora)) {
            throw new CodigoInvalidoException("El código expiró. Pedí uno nuevo.");
        }
        if (token.estaBloqueado()) {
            throw new CodigoInvalidoException("Superaste la cantidad de intentos. Pedí un código nuevo.");
        }
        if (!token.esValido(codigo, ahora)) {
            token.registrarIntentoFallido();
            repo.save(token);
            throw new CodigoInvalidoException("Código inválido");
        }
        return token;
    }

    static String nombre(Usuario usuario) {
        return usuario.getNombre() != null ? usuario.getNombre() : "";
    }
}
