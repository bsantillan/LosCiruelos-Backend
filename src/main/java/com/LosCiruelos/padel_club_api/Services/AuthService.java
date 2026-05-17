package com.LosCiruelos.padel_club_api.Services;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.DTOs.Requests.LoginRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.RegisterRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.GoogleUser;
import com.LosCiruelos.padel_club_api.DTOs.Responses.LoginResponse;
import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.Entities.RefreshToken;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.Exceptions.CuentaDesactivadaException;
import com.LosCiruelos.padel_club_api.Exceptions.EmailEnUsoException;
import com.LosCiruelos.padel_club_api.Exceptions.EmailNoVerificadoException;
import com.LosCiruelos.padel_club_api.Exceptions.PasswordInvalidaException;
import com.LosCiruelos.padel_club_api.Exceptions.TerminosNoAceptadosException;
import com.LosCiruelos.padel_club_api.Security.GoogleTokenVerifier;
import com.LosCiruelos.padel_club_api.Security.JWTUtil;
import com.LosCiruelos.padel_club_api.Security.PasswordValidator;
import com.LosCiruelos.padel_club_api.Security.UsuarioPrincipal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioService usuarioService;
    private final ClienteProfileService clienteProfileService;
    private final PasswordEncoder passwordEncoder;
    private final JWTUtil jwtUtil;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final VerificationService verificationService;

    private LoginResponse buildLoginResponse(Usuario usuario, boolean perfilCompleto, String refreshToken) {
        LoginResponse response = new LoginResponse();
        response.setAccessToken(jwtUtil.generateToken(usuario.getEmail()));
        response.setRefreshToken(refreshToken);
        response.setId(usuario.getId());
        response.setNombre(usuario.getNombre());
        response.setApellido(usuario.getApellido());
        response.setEmail(usuario.getEmail());
        response.setPerfilCompleto(perfilCompleto);
        response.setRol(usuario.getRol());
        return response;
    }

    private Boolean esPerfilCompleto(ClienteProfile clienteProfile) {
        if (clienteProfile == null)
            return false;

        Usuario usuario = clienteProfile.getUsuario();
        return usuario.getTelefono() != null
                && usuario.getNombre() != null
                && usuario.getApellido() != null
                && clienteProfile.getCategoria() != null
                && clienteProfile.getPosicion() != null;
    }

    public void register(RegisterRequest reg_rq) {
        Usuario usuario = usuarioService.findByEmail(reg_rq.getEmail());

        if (usuario != null) {
            throw new EmailEnUsoException();
        }

        if (!PasswordValidator.esValida(reg_rq.getPassword())) {
            throw new PasswordInvalidaException();
        }

        if (!Boolean.TRUE.equals(reg_rq.getTermsAccepted())) {
            throw new TerminosNoAceptadosException();
        }

        Usuario usuario_nuevo = usuarioService.crearUsuario(
                reg_rq.getEmail(),
                reg_rq.getNombre(),
                reg_rq.getApellido(),
                reg_rq.getTelefono(),
                reg_rq.getPassword(),
                Role.CLIENTE,
                AuthProvider.LOCAL,
                reg_rq.getTermsAccepted(),
                false,
                false);

        clienteProfileService.crearClienteProfile(usuario_nuevo, reg_rq.getCategoria(), reg_rq.getPosicion());

        try {
            verificationService.enviarToken(usuario_nuevo.getEmail());
        } catch (Exception e) {
            log.error("No se pudo enviar email de verificación a {}: {}",
                    usuario_nuevo.getEmail(), e.getMessage());
        }
    }

    public LoginResponse login(LoginRequest log_rq) {
        Usuario usuario = usuarioService.findByEmailOrThrow(
                log_rq.getEmail(),
                new CredencialesInvalidasException("Email o contraseña incorrectos"));

        if (!passwordEncoder.matches(log_rq.getPassword(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException("Email o contraseña incorrectos");
        }

        if (!usuario.getEmailVerificado()) {
            throw new EmailNoVerificadoException("Tenés que verificar tu email");
        }

        if (!usuario.getEnabled()) {
            throw new CuentaDesactivadaException();
        }

        jwtUtil.deleteAllRefreshTokens(usuario);

        return this.buildLoginResponse(usuario, true, jwtUtil.createRefreshToken(usuario).getToken());
    }

    public LoginResponse loginWithGoogle(String idToken) {
        try {
            GoogleUser googleUser = googleTokenVerifier.verify(idToken);

            Usuario usuarioGuardado = usuarioService.findByEmail(googleUser.getEmail());
            ClienteProfile perfil = usuarioGuardado != null
                    ? clienteProfileService.findByUsuario(usuarioGuardado)
                    : null;

            if (usuarioGuardado == null) {
                usuarioGuardado = usuarioService.crearUsuario(
                        googleUser.getEmail(), googleUser.getFirstName(), googleUser.getLastName(),
                        null, null, Role.CLIENTE, AuthProvider.GOOGLE, true, true, true);

                perfil = clienteProfileService.crearClienteProfile(usuarioGuardado, null, null);
            } else if (!usuarioGuardado.getEnabled()) {
                throw new CuentaDesactivadaException();
            }

            jwtUtil.deleteAllRefreshTokens(usuarioGuardado);

            return this.buildLoginResponse(usuarioGuardado, esPerfilCompleto(perfil),
                    jwtUtil.createRefreshToken(usuarioGuardado).getToken());

        } catch (CredencialesInvalidasException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Error en loginWithGoogle: {}", ex.getMessage(), ex);
            throw new CredencialesInvalidasException("Token de Google inválido");
        }
    }

    public LoginResponse refreshToken(String token) {
        if (!jwtUtil.validateRefreshToken(token)) {
            throw new CredencialesInvalidasException("Token invalido");
        }

        RefreshToken nuevoRefreshToken = jwtUtil.rotateRefreshToken(token);
        Usuario usuario = nuevoRefreshToken.getUsuario();
        ClienteProfile perfil = clienteProfileService.findByUsuario(usuario);

        return this.buildLoginResponse(usuario, esPerfilCompleto(perfil), nuevoRefreshToken.getToken());
    }

    public void logout(String refreshToken) {

        if (!jwtUtil.validateRefreshToken(refreshToken)) {
            throw new CredencialesInvalidasException("Token inválido");
        }
        Usuario usuarioDelRefresh = jwtUtil.getUsuarioFromToken(refreshToken);

        UsuarioPrincipal principal = (UsuarioPrincipal) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        if (!usuarioDelRefresh.getEmail().equals(principal.getUsername())) {
            throw new CredencialesInvalidasException("Token no pertenece al usuario");
        }

        jwtUtil.deleteRefreshToken(refreshToken);
    }
}
