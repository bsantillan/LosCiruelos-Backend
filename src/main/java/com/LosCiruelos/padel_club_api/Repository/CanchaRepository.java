package com.LosCiruelos.padel_club_api.Repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.Entities.Cancha;

import jakarta.persistence.LockModeType;

public interface CanchaRepository extends JpaRepository<Cancha, Long> {

    /**
     * Lock de escritura sobre la cancha: serializa la creación de reservas en
     * una misma cancha para que dos pedidos simultáneos no tomen el mismo turno.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cancha c WHERE c.id = :id")
    Optional<Cancha> findByIdForUpdate(@Param("id") Long id);
}
