package com.LosCiruelos.padel_club_api.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.TipoProducto;

import jakarta.persistence.LockModeType;

public interface ReservaRepository extends JpaRepository<Reserva, Long>, JpaSpecificationExecutor<Reserva> {

    /**
     * Carga la reserva con un lock de escritura (SELECT ... FOR UPDATE).
     * Toda operación que cambie una reserva o sus pagos la toma primero, así
     * pagos, webhook, expiración y cancelación no se pisan entre sí.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reserva r WHERE r.id = :id")
    Optional<Reserva> findByIdForUpdate(@Param("id") Long id);

    /**
     * Listado paginado ("Mis reservas"): la cancha viene en la misma consulta (join de una
     * relación a-uno, no afecta la paginación); pagos y productos se cargan por lotes (@BatchSize).
     */
    @Override
    @EntityGraph(attributePaths = { "cancha", "cliente" })
    Page<Reserva> findAll(Specification<Reserva> spec, Pageable pageable);

    long countByClienteAndEstado(Usuario cliente, EstadoReserva estado);

    Optional<Reserva> findFirstByClienteAndEstadoOrderByFechaReservaDescHoraInicioDesc(Usuario cliente,
            EstadoReserva estado);

    @Query("""
            SELECT COUNT(r)
            FROM Reserva r
            WHERE r.cliente.id = :clienteId
              AND r.estado = :estado
              AND r.fechaReserva BETWEEN :inicioMes AND :finMes
            """)
    Long countPartidosEsteMes(
            @Param("clienteId") Long clienteId,
            @Param("estado") EstadoReserva estado,
            @Param("inicioMes") LocalDate inicioMes,
            @Param("finMes") LocalDate finMes);

    /**
     * Reservas que ocupan el turno [horaInicio, horaFin) de una cancha.
     * Ocupa el turno toda reserva que no esté CANCELADA ni EXPIRADA, salvo una
     * PENDIENTE vencida sin pagos aprobados ni en proceso (el job la va a expirar).
     * Dos turnos se solapan si: inicio_existente < fin_nuevo AND fin_existente > inicio_nuevo.
     */
    @Query("""
            SELECT r FROM Reserva r
            WHERE r.cancha.id = :canchaId
              AND r.fechaReserva = :fecha
              AND r.horaInicio < :horaFin
              AND r.horaFin > :horaInicio
              AND r.estado NOT IN :inactivos
              AND NOT (r.estado = :pendiente AND r.expiresAt <= :ahora
                       AND NOT EXISTS (SELECT p FROM Pago p WHERE p.reserva = r AND p.estado IN :pagosActivos))
            ORDER BY r.horaInicio
            """)
    List<Reserva> findQueOcupan(
            @Param("canchaId") Long canchaId,
            @Param("fecha") LocalDate fecha,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin,
            @Param("ahora") LocalDateTime ahora,
            @Param("inactivos") Collection<EstadoReserva> inactivos,
            @Param("pendiente") EstadoReserva pendiente,
            @Param("pagosActivos") Collection<EstadoPago> pagosActivos);

    /** Ids de PENDIENTE vencidas sin pagos activos que se superponen con el turno (todavía cuentan para la BD). */
    @Query("""
            SELECT r.id FROM Reserva r
            WHERE r.cancha.id = :canchaId
              AND r.fechaReserva = :fecha
              AND r.horaInicio < :horaFin
              AND r.horaFin > :horaInicio
              AND r.estado = :pendiente
              AND r.expiresAt <= :ahora
              AND NOT EXISTS (SELECT p FROM Pago p WHERE p.reserva = r AND p.estado IN :pagosActivos)
            """)
    List<Long> findPendientesVencidasQueSeSuperponen(
            @Param("canchaId") Long canchaId,
            @Param("fecha") LocalDate fecha,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin,
            @Param("ahora") LocalDateTime ahora,
            @Param("pendiente") EstadoReserva pendiente,
            @Param("pagosActivos") Collection<EstadoPago> pagosActivos);

    /** Ids de reservas PENDIENTE cuyo plazo para pagar ya pasó (para el job de expiración). */
    @Query("SELECT r.id FROM Reserva r WHERE r.estado = :pendiente AND r.expiresAt <= :ahora")
    List<Long> findIdsPendientesVencidas(
            @Param("pendiente") EstadoReserva pendiente,
            @Param("ahora") LocalDateTime ahora);

    /** Id de la reserva PENDIENTE vigente del cliente para el mismo inicio (el cliente cambió la duración). */
    @Query("""
            SELECT r.id FROM Reserva r
            WHERE r.cliente.id = :clienteId
              AND r.cancha.id = :canchaId
              AND r.fechaReserva = :fecha
              AND r.horaInicio = :horaInicio
              AND r.estado = :pendiente
              AND r.expiresAt > :ahora
            """)
    Optional<Long> findPendienteVigenteMismoInicio(
            @Param("clienteId") Long clienteId,
            @Param("canchaId") Long canchaId,
            @Param("fecha") LocalDate fecha,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("ahora") LocalDateTime ahora,
            @Param("pendiente") EstadoReserva pendiente);

    /** Ids de otras PENDIENTE vigentes del cliente en esa cancha y fecha sin pagos activos (eligió otro horario). */
    @Query("""
            SELECT r.id FROM Reserva r
            WHERE r.cliente.id = :clienteId
              AND r.cancha.id = :canchaId
              AND r.fechaReserva = :fecha
              AND r.estado = :pendiente
              AND r.expiresAt > :ahora
              AND NOT EXISTS (SELECT p FROM Pago p WHERE p.reserva = r AND p.estado IN :pagosActivos)
            """)
    List<Long> findPendientesSinPagoActivo(
            @Param("clienteId") Long clienteId,
            @Param("canchaId") Long canchaId,
            @Param("fecha") LocalDate fecha,
            @Param("ahora") LocalDateTime ahora,
            @Param("pendiente") EstadoReserva pendiente,
            @Param("pagosActivos") Collection<EstadoPago> pagosActivos);

    /**
     * Reservas con productos alquilados sin devolver cuyo turno ya empezó (para el personal).
     */
    @Query("""
            SELECT DISTINCT r FROM Reserva r JOIN r.productos rp
            WHERE rp.cantidadDevuelta IS NULL
              AND rp.producto.tipo = :alquiler
              AND r.estado IN :estadosQueSeJuegan
              AND (r.fechaReserva < :hoy OR (r.fechaReserva = :hoy AND r.horaInicio <= :hora))
            ORDER BY r.fechaReserva, r.horaInicio
            """)
    List<Reserva> findConAlquileresSinDevolver(
            @Param("alquiler") TipoProducto alquiler,
            @Param("estadosQueSeJuegan") Collection<EstadoReserva> estadosQueSeJuegan,
            @Param("hoy") LocalDate hoy,
            @Param("hora") LocalTime hora);

    /** Reservas sin recordatorio cuyo turno empieza ese día entre las dos horas (solo ids). */
    @Query("""
            SELECT r.id FROM Reserva r
            WHERE r.estado IN :estados
              AND r.recordatorioEnviadoAt IS NULL
              AND r.fechaReserva = :fecha
              AND r.horaInicio >= :desde AND r.horaInicio <= :hasta
            ORDER BY r.horaInicio
            """)
    List<Long> findIdsParaRecordatorio(
            @Param("estados") Collection<EstadoReserva> estados,
            @Param("fecha") LocalDate fecha,
            @Param("desde") LocalTime desde,
            @Param("hasta") LocalTime hasta);

    /** Reservas en los estados indicados cuyo turno ya terminó (turnos jugados que deben saldo). */
    @EntityGraph(attributePaths = {"cancha", "cliente"})
    @Query("""
            SELECT r FROM Reserva r
            WHERE r.estado IN :estados
              AND (r.fechaReserva < :hoy OR (r.fechaReserva = :hoy AND r.horaFin <= :hora))
            ORDER BY r.fechaReserva, r.horaInicio
            """)
    List<Reserva> findTerminadasConSaldo(
            @Param("estados") Collection<EstadoReserva> estados,
            @Param("hoy") LocalDate hoy,
            @Param("hora") LocalTime hora);

    /** Marca como COMPLETADA las reservas (pagadas) cuyo turno ya terminó. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Reserva r SET r.estado = :completada
            WHERE r.estado IN :confirmados
              AND (r.fechaReserva < :hoy OR (r.fechaReserva = :hoy AND r.horaFin <= :hora))
            """)
    int marcarCompletadas(
            @Param("completada") EstadoReserva completada,
            @Param("confirmados") Collection<EstadoReserva> confirmados,
            @Param("hoy") LocalDate hoy,
            @Param("hora") LocalTime hora);
}
