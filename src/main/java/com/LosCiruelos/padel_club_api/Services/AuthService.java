package com.LosCiruelos.padel_club_api.Services;

import java.time.LocalDateTime;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.DTOs.Requests.LoginRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.RegisterRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.GoogleUser;
import com.LosCiruelos.padel_club_api.DTOs.Responses.LoginResponse;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Exceptions.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.Exceptions.EmailEnUsoException;
import com.LosCiruelos.padel_club_api.Exceptions.EmailNoVerificadoException;
import com.LosCiruelos.padel_club_api.Exceptions.PasswordInvalidaException;
import com.LosCiruelos.padel_club_api.Exceptions.TerminosNoAceptadosException;
import com.LosCiruelos.padel_club_api.Repository.RefreshTokenRepository;
import com.LosCiruelos.padel_club_api.Repository.UsuarioRepository;
import com.LosCiruelos.padel_club_api.Security.GoogleTokenVerifier;
import com.LosCiruelos.padel_club_api.Security.JWTUtil;
import com.LosCiruelos.padel_club_api.Security.PasswordValidator;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JWTUtil jwtUtil;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final VerificationService verificationService;

    private String capitalizar(String texto) {
        if (texto == null || texto.isEmpty())
            return texto;
        return texto.substring(0, 1).toUpperCase() + texto.substring(1).toLowerCase();
    }

    public void register(RegisterRequest reg_rq) {
        Usuario usuario = usuarioRepository.findByEmail(reg_rq.getEmail()).orElse(null);

        if (usuario != null) {
            if (usuario.getEmailVerificado()) {
                throw new EmailEnUsoException();
            } else {
                throw new EmailNoVerificadoException("Este email ya está registrado");
            }
        }

        if (!PasswordValidator.esValida(reg_rq.getPassword())) {
            throw new PasswordInvalidaException();
        }

        if (!Boolean.TRUE.equals(reg_rq.getTermsAccepted())) {
            throw new TerminosNoAceptadosException();
        }

        Usuario usuario_nuevo = Usuario.builder()
                .email(reg_rq.getEmail())
                .nombre(capitalizar(reg_rq.getNombre()))
                .apellido(capitalizar(reg_rq.getApellido()))
                .passwordHash(passwordEncoder.encode(reg_rq.getPassword()))
                .termsAccepted(reg_rq.getTermsAccepted())
                .termsAcceptedAt(LocalDateTime.now())
                .provider(AuthProvider.LOCAL)
                .build();
        usuarioRepository.save(usuario_nuevo);

        verificationService.enviarTokenVerificacion(usuario_nuevo.getEmail());
    }

    public LoginResponse login(LoginRequest log_rq) {
        Usuario usuario = usuarioRepository.findByEmail(log_rq.getEmail())
                .orElseThrow(() -> new CredencialesInvalidasException("Email o contraseña incorrectos"));

        if (!passwordEncoder.matches(log_rq.getPassword(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException("Email o contraseña incorrectos");
        }

        if (!usuario.getEmailVerificado()) {
            throw new EmailNoVerificadoException("Tenés que verificar tu email");
        }

        refreshTokenRepository.deleteByUsuario(usuario);

        LoginResponse response = new LoginResponse();
        response.setAccessToken(jwtUtil.generateToken(usuario.getEmail()));
        response.setRefreshToken(jwtUtil.createRefreshToken(usuario).getToken());
        response.setId(usuario.getId());
        response.setApellido(usuario.getApellido());
        response.setNombre(usuario.getNombre());
        response.setEmail(usuario.getEmail());

        return response;
    }

    public LoginResponse loginWithGoogle(String idToken) {
        try {
            GoogleUser googleUser = googleTokenVerifier.verify(idToken);
            Usuario usuario = usuarioRepository.findByEmail(googleUser.getEmail())
                    .orElseGet(() -> usuarioRepository.save(Usuario.builder()
                            .email(googleUser.getEmail())
                            .nombre(googleUser.getFirstName())
                            .apellido(googleUser.getLastName())
                            .provider(AuthProvider.GOOGLE)
                            .termsAccepted(true)
                            .termsAcceptedAt(LocalDateTime.now())
                            .emailVerificado(true)
                            .enabled(true)
                            .build()));
            LoginResponse response = new LoginResponse();
            response.setAccessToken(jwtUtil.generateToken(usuario.getEmail()));
            response.setRefreshToken(jwtUtil.createRefreshToken(usuario).getToken());
            return response;
        } catch (Exception ex) {
            throw new CredencialesInvalidasException("Token de google invalido");
        }
    }

    public String refreshToken(String token) {

        if (!jwtUtil.validateRefreshToken(token)) {
            throw new CredencialesInvalidasException("Token invalido");
        }

        Usuario usuario = jwtUtil.getUsuarioFromToken(token);
        return (jwtUtil.generateToken(usuario.getEmail()));
    }

    public void logout(String refreshToken) {

        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new CredencialesInvalidasException("Token inválido");
        }
        Usuario usuario = jwtUtil.getUsuarioFromToken(refreshToken);
        jwtUtil.deleteRefreshToken(usuario);
    }
}
