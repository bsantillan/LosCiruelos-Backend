package com.LosCiruelos.padel_club_api.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.Entities.EnvioCodigoTelefono;

public interface EnvioCodigoTelefonoRepository extends JpaRepository<EnvioCodigoTelefono, Long> {

    long countByUsuarioIdAndEnviadoAtAfter(Long usuarioId, LocalDateTime desde);

    long countByTelefonoAndEnviadoAtAfter(String telefono, LocalDateTime desde);

    Optional<EnvioCodigoTelefono> findFirstByUsuarioIdOrderByEnviadoAtDesc(Long usuarioId);

    @Query("SELECT MIN(e.enviadoAt) FROM EnvioCodigoTelefono e WHERE e.usuarioId = :usuarioId AND e.enviadoAt > :desde")
    Optional<LocalDateTime> primeroDelUsuarioDesde(@Param("usuarioId") Long usuarioId, @Param("desde") LocalDateTime desde);

    @Query("SELECT MIN(e.enviadoAt) FROM EnvioCodigoTelefono e WHERE e.telefono = :telefono AND e.enviadoAt > :desde")
    Optional<LocalDateTime> primeroDelNumeroDesde(@Param("telefono") String telefono, @Param("desde") LocalDateTime desde);

    /** Los registros viejos ya no cuentan para ningún tope. */
    @Modifying
    @Query("DELETE FROM EnvioCodigoTelefono e WHERE e.enviadoAt < :antesDe")
    int borrarAnterioresA(@Param("antesDe") LocalDateTime antesDe);
}
