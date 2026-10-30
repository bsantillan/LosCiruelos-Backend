package com.LosCiruelos.padel_club_api.security;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.common.Reloj;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

/**
 * El token de acceso (JWT firmado con HS256, con el email del usuario). Las sesiones (refresh
 * tokens) son de usuarios: {@code RefreshTokenService}.
 */
@Component
@RequiredArgsConstructor
public class JWTUtil {

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.secret}")
    private String secret;

    private final Reloj reloj;

    @PostConstruct
    private void init() {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET debe tener al menos 32 bytes para firmar con HS256");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private SecretKey signingKey;

    public String generateToken(String email) {
        Date ahora = Date.from(reloj.instante());
        return Jwts.builder()
                .subject(email)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expiration * 1000))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public String recuperarMail(String token) {
        try {
            return parser().parseSignedClaims(token).getPayload().getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean validateAccessToken(String token) {
        try {
            parser().parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /** El vencimiento se controla con la hora del {@link Reloj}, la misma con la que se firmó. */
    private JwtParser parser() {
        return Jwts.parser().verifyWith(signingKey).clock(() -> Date.from(reloj.instante())).build();
    }
}
