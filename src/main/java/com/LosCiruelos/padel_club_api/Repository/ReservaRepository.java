package com.LosCiruelos.padel_club_api.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {
    /**
     * Busca reservas que se solapan con el horario solicitado para una cancha y
     * fecha dada.
     * Se excluyen reservas CANCELADAS y EXPIRADAS.
     * Dos turnos se solapan si: inicio_existente < fin_nuevo AND fin_existente >
     * inicio_nuevo
     */
    @Query("""
            SELECT r FROM Reserva r
            WHERE r.cancha.id = :canchaId
              AND r.fechaReserva = :fecha
              AND r.estado NOT IN (:estadosExcluidos)
              AND r.horaInicio < :horaFin
              AND r.horaFin > :horaInicio
            """)
    List<Reserva> findSolapadas(
            @Param("canchaId") Integer canchaId,
            @Param("fecha") LocalDate fecha,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin,
            @Param("estadosExcluidos") List<EstadoReserva> estadosExcluidos);
}
