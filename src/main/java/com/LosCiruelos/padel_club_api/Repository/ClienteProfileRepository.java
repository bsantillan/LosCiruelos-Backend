package com.LosCiruelos.padel_club_api.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.Entities.Usuario;

public interface ClienteProfileRepository extends JpaRepository<ClienteProfile, Long> {
    Optional<ClienteProfile> findByUsuario(Usuario usuario);
}
