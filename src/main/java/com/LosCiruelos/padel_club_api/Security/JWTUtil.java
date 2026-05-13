package com.LosCiruelos.padel_club_api.Security;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.Entities.RefreshToken;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Repository.RefreshTokenRepository;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.transaction.Transactional;
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

    public String generateToken(String email){
        return Jwts.builder()
            .subject(email)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration * 1000))
            .signWith(Keys.hmacShaKeyFor(secret.getBytes()), Jwts.SIG.HS256)
            .compact();  
    }

    public String recuperarMail(String token) {
        return Jwts.parser()
            .verifyWith(Keys.hmacShaKeyFor(secret.getBytes()))
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }

    @Transactional
    public RefreshToken createRefreshToken(Usuario usuario){
        
        RefreshToken refreshToken = new RefreshToken();

        refreshToken.setUsuario(usuario);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setFechaExpiracion(Instant.now().plusSeconds(expirationRefresh));
        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public boolean validateRefreshToken(String token) {
        Optional<RefreshToken> optional = refreshTokenRepository.findByToken(token);

        if (optional.isEmpty()) return false;

        RefreshToken refreshToken = optional.get();

        if (refreshToken.getFechaExpiracion().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken); 
            return false;
        }
        
        return true;
    }

    public Usuario getUsuarioFromToken(String token) {
        return refreshTokenRepository.findByToken(token)
            .map(RefreshToken::getUsuario)
            .orElseThrow(() -> new RuntimeException("Refresh token inválido"));
    }

    @Transactional
    public void deleteRefreshToken(Usuario usuario) {
        refreshTokenRepository.deleteByUsuario(usuario);
    }

    @Scheduled(cron = "0 0 2 * * ?") 
    public void cleanExpiredTokens() {
        refreshTokenRepository.deleteAllByFechaExpiracionBefore(Instant.now());
    }

}
