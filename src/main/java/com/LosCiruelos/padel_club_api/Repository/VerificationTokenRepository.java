package com.LosCiruelos.padel_club_api.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.VerificationToken;
import com.LosCiruelos.padel_club_api.Entities.Enum.TokenType;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

    // DELETE inmediato: con un delete derivado Hibernate insertaba el código
    // nuevo antes de borrar el viejo y chocaba con UNIQUE (usuario_id, type)
    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM VerificationToken vt WHERE vt.usuario = :usuario AND vt.type = :type")
    void deleteByUsuarioAndType(@Param("usuario") Usuario usuario, @Param("type") TokenType type);

    Optional<VerificationToken> findByUsuarioAndType(Usuario usuario, TokenType type);
}
