package com.LosCiruelos.padel_club_api.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;

public interface ReservaRepository extends JpaRepository<Reserva, Long> {

  List<Reserva> findAllByCliente(Usuario cliente);

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
      @Param("canchaId") Long canchaId,
      @Param("fecha") LocalDate fecha,
      @Param("horaInicio") LocalTime horaInicio,
      @Param("horaFin") LocalTime horaFin,
      @Param("estadosExcluidos") List<EstadoReserva> estadosExcluidos);

  List<Reserva> findByEstadoAndExpiresAtBefore(EstadoReserva estado, LocalDateTime fechaHora);

  @Query("""
      SELECT r FROM Reserva r
      WHERE r.cliente.id = :clienteId
        AND r.cancha.id = :canchaId
        AND r.fechaReserva = :fecha
        AND r.horaInicio = :horaInicio
        AND r.estado = 'PENDIENTE'
        AND r.expiresAt > :ahora
      """)
  Optional<Reserva> findPendienteVigenteByClienteAndCanchaAndFechaAndHoraInicio(
      @Param("clienteId") Long clienteId,
      @Param("canchaId") Long canchaId,
      @Param("fecha") LocalDate fecha,
      @Param("horaInicio") LocalTime horaInicio,
      @Param("ahora") LocalDateTime ahora);

  // ✅ Busca reservas PENDIENTE sin pagos aprobados del cliente para una cancha y
  // fecha
  @Query("""
      SELECT r FROM Reserva r
      WHERE r.cliente.id = :clienteId
        AND r.cancha.id = :canchaId
        AND r.fechaReserva = :fecha
        AND r.estado = 'PENDIENTE'
        AND r.expiresAt > :ahora
        AND NOT EXISTS (
          SELECT p FROM Pago p
          WHERE p.reserva = r
          AND p.estado IN ('APROBADO', 'EN_PROCESO')
        )
      """)
  List<Reserva> findPendientesSinPagoByClienteAndCanchaAndFecha(
      @Param("clienteId") Long clienteId,
      @Param("canchaId") Long canchaId,
      @Param("fecha") LocalDate fecha,
      @Param("ahora") LocalDateTime ahora);
}
