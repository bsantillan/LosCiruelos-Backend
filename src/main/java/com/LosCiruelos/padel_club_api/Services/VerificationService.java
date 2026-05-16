package com.LosCiruelos.padel_club_api.Services;

import java.util.Map;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.VerificationToken;
import com.LosCiruelos.padel_club_api.Entities.Enum.TokenType;
import com.LosCiruelos.padel_club_api.Exceptions.CodigoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.CuentaVerificadaException;
import com.LosCiruelos.padel_club_api.Repository.UsuarioRepository;
import com.LosCiruelos.padel_club_api.Repository.VerificationTokenRepository;
import com.LosCiruelos.padel_club_api.Services.Email.EmailServiceFactory;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final UsuarioRepository usuarioRepository;
    private final VerificationTokenRepository tokenRepository;
    private final EmailServiceFactory emailServiceFactory;

    @Transactional
    public void enviarToken(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        tokenRepository.deleteByUsuarioAndType(usuario, TokenType.VERIFY_EMAIL);

        VerificationToken token = VerificationToken.builder()
                .usuario(usuario)
                .token(VerificationToken.generarCodigo())
                .type(TokenType.VERIFY_EMAIL)
                .fechaExpiracion(Instant.now().plus(1, ChronoUnit.DAYS))
                .build();

        tokenRepository.save(token);

        emailServiceFactory.getService().sendVerificationEmail(
                usuario.getEmail(),
                Map.of("nombre", usuario.getNombre(), "codigo", token.getToken()));
    }

    @Transactional
    public void verificarToken(String email, String codigo) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (usuario.getEmailVerificado()) {
            throw new CuentaVerificadaException();
        }

        VerificationToken token = tokenRepository
                .findByUsuarioAndType(usuario, TokenType.VERIFY_EMAIL)
                .orElseThrow(() -> new CodigoInvalidoException("Código invalido"));

        if (token.esExpirado()) {
            throw new CodigoInvalidoException("Codigo inválido");
        }

        if (!token.esValido(codigo)) {
            throw new CodigoInvalidoException("Código inválido");
        }

        usuario.setEmailVerificado(true);
        usuario.setEnabled(true);
        usuarioRepository.save(usuario);

        tokenRepository.delete(token);
    }

    @Transactional
    public void reenviarToken(String email) {
        Usuario usuario = usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (usuario.getEmailVerificado()) {
            throw new CuentaVerificadaException();
        }

        tokenRepository.deleteByUsuarioAndType(usuario, TokenType.VERIFY_EMAIL);

        enviarToken(email);
    }
}
