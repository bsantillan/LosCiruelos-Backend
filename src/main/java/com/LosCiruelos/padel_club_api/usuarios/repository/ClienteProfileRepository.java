package com.LosCiruelos.padel_club_api.usuarios.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.LosCiruelos.padel_club_api.usuarios.entity.ClienteProfile;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;

public interface ClienteProfileRepository extends JpaRepository<ClienteProfile, Long> {
    Optional<ClienteProfile> findByUsuario(Usuario usuario);
}
