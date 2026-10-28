package com.LosCiruelos.padel_club_api.usuarios.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.usuarios.dto.request.LoginRequest;
import com.LosCiruelos.padel_club_api.usuarios.dto.request.RegisterRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.GoogleUser;
import com.LosCiruelos.padel_club_api.usuarios.dto.response.LoginResponse;
import com.LosCiruelos.padel_club_api.usuarios.entity.RefreshToken;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.AuthProvider;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
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

    private boolean perfilCompleto(Usuario usuario) {
        return clienteProfileService.perfilCompleto(usuario);
    }

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

    @Transactional
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
                false, // email sin verificar
                true); // cuenta habilitada: "enabled" solo se usa para desactivar cuentas

        clienteProfileService.crearClienteProfile(usuario_nuevo, reg_rq.getCategoria(), reg_rq.getPosicion());

        // consentimiento de notificaciones; arrancan por mail (el medio se cambia en el perfil,
        // después de validar el celular)
        usuario_nuevo.setNotificacionesAceptadasAt(usuario_nuevo.getTermsAcceptedAt());
        usuario_nuevo.setCanalNotificaciones(CanalNotificacion.EMAIL);
        usuarioService.save(usuario_nuevo);

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

        return this.buildLoginResponse(usuario, perfilCompleto(usuario), jwtUtil.createRefreshToken(usuario).getToken());
    }

    @Transactional
    public LoginResponse loginWithGoogle(String idToken) {
        GoogleUser googleUser = googleTokenVerifier.verify(idToken);

        Usuario usuarioGuardado = usuarioService.findByEmail(googleUser.getEmail());

        if (usuarioGuardado == null) {
            usuarioGuardado = usuarioService.crearUsuario(
                    googleUser.getEmail(), googleUser.getFirstName(), googleUser.getLastName(),
                    null, null, Role.CLIENTE, AuthProvider.GOOGLE, true, true, true);

            clienteProfileService.crearClienteProfile(usuarioGuardado, null, null);
        } else {
            // Usuario existente, sin importar el provider. "enabled" = false solo si fue desactivado.
            if (!usuarioGuardado.getEnabled()) {
                throw new CuentaDesactivadaException();
            }

            // Google confirmó que el email es suyo: queda verificado
            if (!usuarioGuardado.getEmailVerificado()) {
                usuarioGuardado.setEmailVerificado(true);
                usuarioService.save(usuarioGuardado);
            }
        }

        jwtUtil.deleteAllRefreshTokens(usuarioGuardado);

        return this.buildLoginResponse(usuarioGuardado, perfilCompleto(usuarioGuardado),
                jwtUtil.createRefreshToken(usuarioGuardado).getToken());
    }

    public LoginResponse refreshToken(String token) {
        if (!jwtUtil.validateRefreshToken(token)) {
            throw new CredencialesInvalidasException("Token invalido");
        }

        if (!jwtUtil.getUsuarioFromToken(token).getEnabled()) {
            jwtUtil.deleteRefreshToken(token);
            throw new CuentaDesactivadaException();
        }

        RefreshToken nuevoRefreshToken = jwtUtil.rotateRefreshToken(token);
        Usuario usuario = nuevoRefreshToken.getUsuario();

        return this.buildLoginResponse(usuario, perfilCompleto(usuario), nuevoRefreshToken.getToken());
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
