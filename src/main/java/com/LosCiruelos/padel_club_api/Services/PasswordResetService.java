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
import com.LosCiruelos.padel_club_api.Exceptions.PasswordInvalidaException;
import com.LosCiruelos.padel_club_api.Repository.UsuarioRepository;
import com.LosCiruelos.padel_club_api.Repository.VerificationTokenRepository;
import com.LosCiruelos.padel_club_api.Security.PasswordValidator;
import com.LosCiruelos.padel_club_api.Services.Email.EmailServiceFactory;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

        private final UsuarioRepository usuarioRepository;
        private final VerificationTokenRepository tokenRepository;
        private final PasswordEncoder passwordEncoder;
        private final EmailServiceFactory emailServiceFactory;

        @Transactional
        public void enviarToken(String email) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

                tokenRepository.deleteByUsuarioAndType(usuario, TokenType.RESET_PASSWORD);

                VerificationToken token = VerificationToken.builder()
                                .usuario(usuario)
                                .token(VerificationToken.generarCodigo())
                                .type(TokenType.RESET_PASSWORD)
                                .fechaExpiracion(Instant.now().plus(15, ChronoUnit.MINUTES))
                                .build();

                tokenRepository.save(token);

                emailServiceFactory.getService().sendResetPasswordEmail(
                                usuario.getEmail(),
                                Map.of("nombre", usuario.getNombre(), "codigo", token.getToken()));
        }

        @Transactional
        public void verificarToken(String email, String codigo) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

                VerificationToken token = tokenRepository
                                .findByUsuarioAndType(usuario, TokenType.RESET_PASSWORD)
                                .orElseThrow(() -> new CodigoInvalidoException("Código invalido"));

                if (token.esExpirado()) {
                        throw new CodigoInvalidoException("Codigo inválido");
                }

                if (!token.esValido(codigo)) {
                        throw new CodigoInvalidoException("Código inválido");
                }

                tokenRepository.delete(token);
        }

        @Transactional
        public void reenviarToken(String email) {
                Usuario usuario = usuarioRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

                tokenRepository.deleteByUsuarioAndType(usuario, TokenType.RESET_PASSWORD);

                enviarToken(email);
        }

        public void resetPassword(String email, String nuevaPassword) {

                if (!PasswordValidator.esValida(nuevaPassword)) {
                        throw new PasswordInvalidaException();
                }
                Usuario usuario = usuarioRepository.findByEmail(email)
                                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

                usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
                usuarioRepository.save(usuario);
        }
}