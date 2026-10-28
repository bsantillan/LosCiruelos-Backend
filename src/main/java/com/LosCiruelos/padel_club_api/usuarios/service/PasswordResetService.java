package com.LosCiruelos.padel_club_api.usuarios.service;

import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.usuarios.repository.UsuarioRepository;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.VerificationToken;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.TokenType;
import com.LosCiruelos.padel_club_api.Exceptions.CodigoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.Exceptions.PasswordInvalidaException;
import com.LosCiruelos.padel_club_api.usuarios.repository.VerificationTokenRepository;
import com.LosCiruelos.padel_club_api.Security.JWTUtil;
import com.LosCiruelos.padel_club_api.Security.PasswordValidator;
import com.LosCiruelos.padel_club_api.notificaciones.email.EmailServiceFactory;
import com.LosCiruelos.padel_club_api.notificaciones.email.EnvioPostCommit;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

        private final UsuarioService usuarioService;
        private final UsuarioRepository usuarioRepository;
        private final VerificationTokenRepository tokenRepository;
        private final PasswordEncoder passwordEncoder;
        private final EmailServiceFactory emailServiceFactory;
        private final JWTUtil jwtUtil;
        private final Reloj reloj;

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
                                .fechaExpiracion(reloj.instante().plus(15, ChronoUnit.MINUTES))
                                .build());

                Map<String, Object> datos = Map.of("nombre", VerificationService.nombre(usuario), "codigo",
                                token.getToken());
                EnvioPostCommit.ejecutar("reset de contraseña a " + usuario.getEmail(),
                                () -> emailServiceFactory.getService().sendResetPasswordEmail(usuario.getEmail(), datos));
        }

        @Transactional(noRollbackFor = CodigoInvalidoException.class)
        public void verificarToken(String email, String codigo) {
                Usuario usuario = usuarioService.autenticado(email);

                VerificationToken token = VerificationService.validarCodigo(tokenRepository, usuario,
                                TokenType.PASSWORD_RESET, codigo, reloj.instante());
                token.setVerificado(true);
                tokenRepository.save(token);
        }

        @Transactional
        public void resetPassword(String email, String nuevaPassword) {
                if (!PasswordValidator.esValida(nuevaPassword)) {
                        throw new PasswordInvalidaException();
                }
                Usuario usuario = usuarioService.autenticado(email);

                VerificationToken token = tokenRepository
                                .findByUsuarioAndType(usuario, TokenType.PASSWORD_RESET)
                                .orElseThrow(() -> new CredencialesInvalidasException("Flujo inválido"));

                if (!token.getVerificado() || token.esExpirado(reloj.instante())) {
                        throw new CredencialesInvalidasException("Flujo inválido");
                }

                usuario.setPasswordHash(passwordEncoder.encode(nuevaPassword));
                usuarioRepository.save(usuario);
                tokenRepository.delete(token);
                // cierra las sesiones abiertas con la contraseña anterior
                jwtUtil.deleteAllRefreshTokens(usuario);
        }
}
