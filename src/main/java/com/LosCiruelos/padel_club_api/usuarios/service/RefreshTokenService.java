package com.LosCiruelos.padel_club_api.usuarios.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.usuarios.entity.RefreshToken;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.exception.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.usuarios.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;

/**
 * Las sesiones de cada usuario (refresh tokens): se crean al iniciar sesión, se rotan al renovar
 * el token de acceso y se borran al cerrar sesión o desactivar la cuenta. El token de acceso lo
 * firma {@code JWTUtil}.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final Reloj reloj;

    @Value("${refresh_token.expiration}")
    private long expiracionSegundos;

    @Transactional
    public RefreshToken crear(Usuario usuario) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsuario(usuario);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setFechaExpiracion(reloj.instante().plusSeconds(expiracionSegundos));
        return refreshTokenRepository.save(refreshToken);
    }

    /** Si existe y no venció; uno vencido se borra. */
    @Transactional
    public boolean esValido(String token) {
        Optional<RefreshToken> optional = refreshTokenRepository.findByToken(token);
        if (optional.isEmpty()) {
            return false;
        }
        RefreshToken refreshToken = optional.get();
        if (refreshToken.getFechaExpiracion().isBefore(reloj.instante())) {
            refreshTokenRepository.delete(refreshToken);
            return false;
        }
        return true;
    }

    public Usuario usuarioDelToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .map(RefreshToken::getUsuario)
                .orElseThrow(() -> new CredencialesInvalidasException("Refresh token inválido"));
    }

    @Transactional
    public void borrar(String token) {
        refreshTokenRepository.deleteByToken(token);
    }

    /** Cierra todas las sesiones del usuario. */
    @Transactional
    public void borrarTodos(Usuario usuario) {
        refreshTokenRepository.deleteByUsuario(usuario);
    }

    /** Cambia el token por uno nuevo: el viejo deja de servir. */
    @Transactional
    public RefreshToken rotar(String tokenViejo) {
        RefreshToken viejo = refreshTokenRepository.findByToken(tokenViejo)
                .orElseThrow(() -> new CredencialesInvalidasException("Refresh token inválido"));

        Usuario usuario = viejo.getUsuario();
        refreshTokenRepository.delete(viejo);
        refreshTokenRepository.flush();

        RefreshToken nuevo = new RefreshToken();
        nuevo.setUsuario(usuario);
        nuevo.setToken(UUID.randomUUID().toString());
        nuevo.setFechaExpiracion(reloj.instante().plusSeconds(expiracionSegundos));
        return refreshTokenRepository.save(nuevo);
    }

    // El DELETE del repositorio tiene su propia transacción (antes fallaba todas las noches)
    public void borrarVencidos() {
        refreshTokenRepository.deleteAllByFechaExpiracionBefore(reloj.instante());
    }
}
