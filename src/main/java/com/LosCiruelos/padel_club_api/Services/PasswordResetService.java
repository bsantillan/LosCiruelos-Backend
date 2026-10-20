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
import com.LosCiruelos.padel_club_api.Security.JWTUtil;
import com.LosCiruelos.padel_club_api.Security.PasswordValidator;
import com.LosCiruelos.padel_club_api.Services.Email.EmailServiceFactory;
import com.LosCiruelos.padel_club_api.Services.Email.EnvioPostCommit;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

        private final UsuarioService usuarioService;
        private final VerificationTokenRepository tokenRepository;
        private final PasswordEncoder passwordEncoder;
        private final EmailServiceFactory emailServiceFactory;
        private final JWTUtil jwtUtil;

        @Transactional
        public void enviarToken(String email) {
                Usuario usuario = usuarioService.findByEmail(email);
                if (usuario == null) {
                        return; // no se revela si el email existe
                }

                tokenRepository.deleteByUsuarioAndType(usuario, TokenType.PASSWORD_RESET);
                VerificationToken token = tokenRepository.save(VerificationToken.builder()
                                .usuario(usuario)
                                .token(VerificationToken.generarCodigo())
                                .type(TokenType.PASSWORD_RESET)
                                .fechaExpiracion(Instant.now().plus(15, ChronoUnit.MINUTES))
                                .build());

                Map<String, Object> datos = Map.of("nombre", VerificationService.nombre(usuario), "codigo",
                                token.getToken());
                EnvioPostCommit.ejecutar("reset de contraseña a " + usuario.getEmail(),
                                () -> emailServiceFactory.getService().sendResetPasswordEmail(usuario.getEmail(), datos));
        }

        @Transactional(noRollbackFor = CodigoInvalidoException.class)
        public void verificarToken(String email, String codigo) {
                Usuario usuario = usuarioService.findByEmailOrThrow(email,
                                new CredencialesInvalidasException("Usuario no encontrado"));

                VerificationToken token = VerificationService.validarCodigo(tokenRepository, usuario,
                                TokenType.PASSWORD_RESET, codigo);
                token.setVerificado(true);
                tokenRepository.save(token);
        }

        @Transactional
        public void resetPassword(String email, String nuevaPassword) {
                if (!PasswordValidator.esValida(nuevaPassword)) {
                        throw new PasswordInvalidaException();
                }
                Usuario usuario = usuarioService.findByEmailOrThrow(email,
                                new CredencialesInvalidasException("Usuario no encontrado"));

                VerificationToken token = tokenRepository
                                .findByUsuarioAndType(usuario, TokenType.PASSWORD_RESET)
                                .orElseThrow(() -> new CredencialesInvalidasException("Flujo inválido"));

                if (!token.getVerificado() || token.esExpirado()) {
                        throw new CredencialesInvalidasException("Flujo inválido");
                }

                usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
                usuarioService.save(usuario);
                tokenRepository.delete(token);
                // cierra las sesiones abiertas con la contraseña anterior
                jwtUtil.deleteAllRefreshTokens(usuario);
        }
}
