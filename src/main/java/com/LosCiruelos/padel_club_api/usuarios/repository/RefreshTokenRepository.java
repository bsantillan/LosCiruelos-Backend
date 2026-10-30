package com.LosCiruelos.padel_club_api.usuarios.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.usuarios.entity.RefreshToken;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByUsuario(Usuario usuario);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.usuario = :usuario")
    void deleteByUsuario(@Param("usuario") Usuario usuario);

    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.token = :token")
    void deleteByToken(@Param("token") String token);

    // DELETE directo: un delete derivado sin transacción fallaba en el job nocturno
    @Modifying
    @Transactional
    @Query("DELETE FROM RefreshToken rt WHERE rt.fechaExpiracion < :fecha")
    int deleteAllByFechaExpiracionBefore(@Param("fecha") Instant fecha);
}
