package com.LosCiruelos.padel_club_api.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;

public interface UsuarioRepository extends JpaRepository<Usuario, Long>, JpaSpecificationExecutor<Usuario> {
    Optional<Usuario> findByEmail(String email);

    List<Usuario> findByRolAndEnabledTrue(Role rol);

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
