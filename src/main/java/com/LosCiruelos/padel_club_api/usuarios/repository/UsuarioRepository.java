package com.LosCiruelos.padel_club_api.usuarios.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {
    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByRolAndEnabledTrue(Role rol);

    /** Página con el perfil de jugador en la misma consulta (búsqueda de clientes del personal). */
    @Override
    @EntityGraph(attributePaths = { "clienteProfile" })
    Page<Usuario> findAll(Specification<Usuario> spec, Pageable pageable);

    /** Otra cuenta activa con ese celular verificado (idActual = -1 para un usuario nuevo). */
    @Query("""
            SELECT COUNT(u) > 0 FROM Usuario u
            WHERE u.telefono = :telefono AND u.telefonoVerificado = true AND u.enabled = true
              AND u.id <> :idActual
            """)
    boolean existeCelularVerificadoEnOtraCuentaActiva(@Param("telefono") String telefono,
            @Param("idActual") Long idActual);

    /** Cuentas (desactivadas) que tenían ese celular verificado: pierden la verificación. */
    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE Usuario u SET u.telefonoVerificado = false
            WHERE u.telefono = :telefono AND u.telefonoVerificado = true AND u.id <> :idActual
            """)
    int quitarVerificacionDeOtrasCuentas(@Param("telefono") String telefono, @Param("idActual") Long idActual);
}
