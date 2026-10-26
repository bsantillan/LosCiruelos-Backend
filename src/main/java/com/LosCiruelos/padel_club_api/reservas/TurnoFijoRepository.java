package com.LosCiruelos.padel_club_api.reservas;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


import jakarta.persistence.LockModeType;

public interface TurnoFijoRepository extends JpaRepository<TurnoFijo, Long> {

    /** Lock del turno fijo: la creación de sus partidos, los cambios y la baja no se pisan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tf FROM TurnoFijo tf WHERE tf.id = :id")
    Optional<TurnoFijo> findByIdForUpdate(@Param("id") Long id);

    List<TurnoFijo> findByActivoOrderByDiaSemanaAscHoraInicioAsc(Boolean activo);

    List<TurnoFijo> findAllByOrderByActivoDescDiaSemanaAscHoraInicioAsc();

    @Query("SELECT tf.id FROM TurnoFijo tf WHERE tf.activo = true")
    List<Long> findIdsActivos();

    /**
     * Turnos fijos activos que bloquean [horaInicio, horaFin) de esa cancha en esa fecha: mismo
     * día de la semana, ya empezaron y todavía no tienen una reserva para esa fecha (si la tienen,
     * esa reserva ocupa el turno o, si está cancelada, la fecha quedó libre).
     */
    @Query("""
            SELECT tf FROM TurnoFijo tf
            WHERE tf.activo = true
              AND tf.cancha.id = :canchaId
              AND tf.diaSemana = :dia
              AND tf.desde <= :fecha
              AND tf.horaInicio < :horaFin
              AND tf.horaFin > :horaInicio
              AND NOT EXISTS (SELECT r FROM Reserva r WHERE r.turnoFijo = tf AND r.fechaReserva = :fecha)
            """)
    List<TurnoFijo> findQueBloquean(
            @Param("canchaId") Long canchaId,
            @Param("dia") DayOfWeek dia,
            @Param("fecha") LocalDate fecha,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin);

    /** Otros turnos fijos activos de la cancha que se superponen ese día de la semana. */
    @Query("""
            SELECT tf FROM TurnoFijo tf
            WHERE tf.activo = true
              AND tf.id <> :excluirId
              AND tf.cancha.id = :canchaId
              AND tf.diaSemana = :dia
              AND tf.horaInicio < :horaFin
              AND tf.horaFin > :horaInicio
            """)
    List<TurnoFijo> findSuperpuestos(
            @Param("excluirId") Long excluirId,
            @Param("canchaId") Long canchaId,
            @Param("dia") DayOfWeek dia,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin);
}
