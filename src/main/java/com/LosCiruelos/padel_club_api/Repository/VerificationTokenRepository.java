package com.LosCiruelos.padel_club_api.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.VerificationToken;
import com.LosCiruelos.padel_club_api.Entities.Enum.TokenType;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {
    
    void deleteByUsuarioAndType(Usuario usuario, TokenType type);

    Optional<VerificationToken> findByUsuarioAndType(Usuario usuario, TokenType type);
    
}
