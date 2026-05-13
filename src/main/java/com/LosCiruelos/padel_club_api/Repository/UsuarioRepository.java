package com.LosCiruelos.padel_club_api.Repository;

import java.util.Optional;

import org.springframework.data.repository.CrudRepository;

import com.LosCiruelos.padel_club_api.Entities.Usuario;

public interface UsuarioRepository extends CrudRepository<Usuario, Long> {
    Optional<Usuario> findByEmail(String email);
}
