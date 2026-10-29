package com.LosCiruelos.padel_club_api.security;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.usuarios.entity.RefreshToken;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.exception.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.usuarios.repository.RefreshTokenRepository;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JWTUtil {

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${refresh_token.expiration}")
    private long expirationRefresh;

    private final RefreshTokenRepository refreshTokenRepository;

    @PostConstruct
    private void init() {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes para firmar con HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private SecretKey signingKey;

    public String generateToken(String email) {
        return Jwts.builder()
                .subject(email)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public String recuperarMail(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    @Transactional
    public RefreshToken createRefreshToken(Usuario usuario) {

        RefreshToken refreshToken = new RefreshToken();

        refreshToken.setUsuario(usuario);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setFechaExpiracion(Instant.now().plusSeconds(expirationRefresh));
        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public boolean validateRefreshToken(String token) {
        Optional<RefreshToken> optional = refreshTokenRepository.findByToken(token);

        if (optional.isEmpty())
            return false;

        RefreshToken refreshToken = optional.get();

        if (refreshToken.getFechaExpiracion().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            return false;
        }

        return true;
    }

    public boolean validateAccessToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public Usuario getUsuarioFromToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .map(RefreshToken::getUsuario)
                .orElseThrow(() -> new CredencialesInvalidasException("Refresh token inválido"));
    }

    @Transactional
    public void deleteRefreshToken(String token) {
        refreshTokenRepository.deleteByToken(token);
    }

    @Transactional
    public void deleteAllRefreshTokens(Usuario usuario) {
        refreshTokenRepository.deleteByUsuario(usuario);
    }

    @Transactional
    public RefreshToken rotateRefreshToken(String oldToken) {
        RefreshToken old = refreshTokenRepository.findByToken(oldToken)
                .orElseThrow(() -> new CredencialesInvalidasException("Refresh token inválido"));

        Usuario usuario = old.getUsuario();
        refreshTokenRepository.delete(old);

        refreshTokenRepository.flush();

        RefreshToken nuevo = new RefreshToken();
        nuevo.setUsuario(usuario);
        nuevo.setToken(UUID.randomUUID().toString());
        nuevo.setFechaExpiracion(Instant.now().plusSeconds(expirationRefresh));
        return refreshTokenRepository.save(nuevo);
    }

    // El DELETE del repositorio tiene su propia transacción (antes fallaba todas las noches)
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredTokens() {
        refreshTokenRepository.deleteAllByFechaExpiracionBefore(Instant.now());
    }

}
