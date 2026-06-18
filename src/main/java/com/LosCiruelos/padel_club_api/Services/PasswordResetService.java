package com.LosCiruelos.padel_club_api.Services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.VerificationToken;
import com.LosCiruelos.padel_club_api.Entities.Enum.TokenType;
import com.LosCiruelos.padel_club_api.Exceptions.CodigoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.Exceptions.PasswordInvalidaException;
import com.LosCiruelos.padel_club_api.Repository.VerificationTokenRepository;
import com.LosCiruelos.padel_club_api.Security.PasswordValidator;
import com.LosCiruelos.padel_club_api.Services.Email.EmailServiceFactory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

        private final UsuarioService usuarioService;
        private final VerificationTokenRepository tokenRepository;
        private final PasswordEncoder passwordEncoder;
        private final EmailServiceFactory emailServiceFactory;

        @Transactional
        public void enviarToken(String email) {
                Usuario usuario = usuarioService.findByEmail(email);

                if (usuario == null)
                        return;

                tokenRepository.deleteByUsuarioAndType(usuario, TokenType.PASSWORD_RESET);

                VerificationToken token = VerificationToken.builder()
                                .usuario(usuario)
                                .token(VerificationToken.generarCodigo())
                                .type(TokenType.PASSWORD_RESET)
                                .fechaExpiracion(Instant.now().plus(15, ChronoUnit.MINUTES))
                                .build();

                tokenRepository.save(token);

                emailServiceFactory.getService().sendResetPasswordEmail(
                                usuario.getEmail(),
                                Map.of("nombre", usuario.getNombre(), "codigo", token.getToken()));
        }

        @Transactional
        public void verificarToken(String email, String codigo) {
                Usuario usuario = usuarioService.findByEmailOrThrow(email,
                                new CredencialesInvalidasException("Usuario no encontrado"));

                VerificationToken token = tokenRepository
                                .findByUsuarioAndType(usuario, TokenType.PASSWORD_RESET)
                                .orElseThrow(() -> new CodigoInvalidoException("Código invalido"));

                if (token.esExpirado()) {
                        throw new CodigoInvalidoException("Codigo inválido");
                }

                if (!token.esValido(codigo)) {
                        throw new CodigoInvalidoException("Código inválido");
                }

                token.setVerificado(true);
                tokenRepository.save(token);
        }

        public void resetPassword(String email, String nuevaPassword) {

                if (!PasswordValidator.esValida(nuevaPassword)) {
                        throw new PasswordInvalidaException();
                }
                Usuario usuario = usuarioService.findByEmailOrThrow(email,
                                new CredencialesInvalidasException("Usuario no encontrado"));

                VerificationToken token = tokenRepository
                                .findByUsuarioAndType(usuario, TokenType.PASSWORD_RESET)
                                .orElseThrow(() -> new CredencialesInvalidasException("Flujo inválido"));

                if (!token.getVerificado()) {
                        throw new CredencialesInvalidasException("Flujo inválido");
                }

                usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
                usuarioService.save(usuario);
                tokenRepository.delete(token);
        }
}