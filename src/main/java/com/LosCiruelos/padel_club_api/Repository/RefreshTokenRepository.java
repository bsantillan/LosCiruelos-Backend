package com.LosCiruelos.padel_club_api.Repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.LosCiruelos.padel_club_api.Entities.RefreshToken;
import com.LosCiruelos.padel_club_api.Entities.Usuario;

import jakarta.transaction.Transactional;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUsuario(Usuario usuario);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.usuario = :usuario")
    void deleteByUsuario(Usuario usuario);

    void deleteAllByFechaExpiracionBefore(Instant expiryDate);
}
