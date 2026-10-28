package com.LosCiruelos.padel_club_api.reservas.repository;

import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import java.math.BigDecimal;
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

import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.common.ConsultasDeTurno;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijo;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;

import jakarta.persistence.LockModeType;

public interface ReservaRepository extends JpaRepository<Reserva, Long>, JpaSpecificationExecutor<Reserva>,
        ReservaConsultas {

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
              AND\s""" + ConsultasDeTurno.SE_SUPERPONE + """
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
              AND\s""" + ConsultasDeTurno.SE_SUPERPONE + """
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
              AND\s""" + ConsultasDeTurno.EMPEZO + """
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
              AND\s""" + ConsultasDeTurno.TERMINADO + """
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
              AND\s""" + ConsultasDeTurno.TERMINADO + """
            """)
    int marcarCompletadas(
            @Param("completada") EstadoReserva completada,
            @Param("confirmados") Collection<EstadoReserva> confirmados,
            @Param("hoy") LocalDate hoy,
            @Param("hora") LocalTime hora);

    // ── Turnos fijos ──

    boolean existsByTurnoFijoAndFechaReserva(TurnoFijo turnoFijo, LocalDate fechaReserva);

    Optional<Reserva> findByTurnoFijoAndFechaReserva(TurnoFijo turnoFijo, LocalDate fechaReserva);

    /** Ids de los partidos del turno fijo cuyo turno todavía no empezó (de cualquier estado). */
    @Query("""
            SELECT r.id FROM Reserva r
            WHERE r.turnoFijo.id = :turnoFijoId
              AND NOT\s""" + ConsultasDeTurno.EMPEZO + """
            ORDER BY r.fechaReserva
            """)
    List<Long> findIdsFuturosDelTurnoFijo(
            @Param("turnoFijoId") Long turnoFijoId,
            @Param("hoy") LocalDate hoy,
            @Param("hora") LocalTime hora);

    /** Reservas que ocupan la cancha desde una fecha en ese horario (para avisar choques de un turno fijo). */
    @Query("""
            SELECT r FROM Reserva r
            WHERE r.cancha.id = :canchaId
              AND r.fechaReserva >= :desde
              AND\s""" + ConsultasDeTurno.SE_SUPERPONE + """
              AND r.estado NOT IN :inactivos
              AND (r.turnoFijo IS NULL OR r.turnoFijo.id <> :turnoFijoId)
            ORDER BY r.fechaReserva
            """)
    List<Reserva> findActivasDesde(
            @Param("canchaId") Long canchaId,
            @Param("desde") LocalDate desde,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin,
            @Param("inactivos") Collection<EstadoReserva> inactivos,
            @Param("turnoFijoId") Long turnoFijoId);

    // ── Productos alquilados (stock de paletas) ──────────────────────────

    /**
     * Alquileres del producto en reservas de cualquier cancha que se superponen con
     * [horaInicio, horaFin). Cuentan las reservas que ocupan su turno: no CANCELADA ni
     * EXPIRADA, salvo PENDIENTE vencidas sin pagos activos (mismo criterio que las canchas).
     * Los alquileres ya devueltos no cuentan.
     */
    @Query("""
            SELECT new com.LosCiruelos.padel_club_api.reservas.repository.UsoAlquiler(
                       r.horaInicio, r.horaFin, rp.cantidad)
            FROM ReservaProducto rp JOIN rp.reserva r
            WHERE rp.producto.id = :productoId
              AND rp.cantidadDevuelta IS NULL
              AND r.id <> :excluirReservaId
              AND r.fechaReserva = :fecha
              AND\s""" + ConsultasDeTurno.SE_SUPERPONE + """
              AND r.estado NOT IN :inactivos
              AND NOT (r.estado = :pendiente AND r.expiresAt <= :ahora
                       AND NOT EXISTS (SELECT p FROM Pago p WHERE p.reserva = r AND p.estado IN :pagosActivos))
            """)
    List<UsoAlquiler> findAlquileresQueSeSuperponen(
            @Param("productoId") Long productoId,
            @Param("excluirReservaId") Long excluirReservaId,
            @Param("fecha") LocalDate fecha,
            @Param("horaInicio") LocalTime horaInicio,
            @Param("horaFin") LocalTime horaFin,
            @Param("ahora") LocalDateTime ahora,
            @Param("inactivos") Collection<EstadoReserva> inactivos,
            @Param("pendiente") EstadoReserva pendiente,
            @Param("pagosActivos") Collection<EstadoPago> pagosActivos);

    /**
     * Unidades alquiladas del producto en turnos que ya terminaron y cuya devolución todavía
     * no se registró: siguen afuera del club y no se pueden volver a alquilar.
     */
    @Query("""
            SELECT COALESCE(SUM(rp.cantidad), 0)
            FROM ReservaProducto rp JOIN rp.reserva r
            WHERE rp.producto.id = :productoId
              AND rp.cantidadDevuelta IS NULL
              AND r.estado IN :estadosQueSeJuegan
              AND\s""" + ConsultasDeTurno.TERMINADO + """
            """)
    Long sumarSinDevolver(
            @Param("productoId") Long productoId,
            @Param("estadosQueSeJuegan") Collection<EstadoReserva> estadosQueSeJuegan,
            @Param("hoy") LocalDate hoy,
            @Param("hora") LocalTime hora);

    // ── Resumen del período (panel del club) ──────────────────────────────
    // Todo se suma en la base, sin traer reservas a memoria.

    /** Reserva confirmada (se juega o se jugó) con el turno terminado. */
    String RESUMEN_JUGADA = "r.estado IN :juegan AND " + ConsultasDeTurno.TERMINADO;

    /** Reserva confirmada con el turno por terminar. */
    String RESUMEN_POR_JUGAR = "r.estado IN :juegan AND NOT " + ConsultasDeTurno.TERMINADO;

    /**
     * Lo que el club se queda de un pago, igual que Pago.getMontoRetenido: aprobado menos lo
     * devuelto; de un pago devuelto (o por devolver), lo que no se devolvió; el resto, nada.
     */
    String RESUMEN_RETENIDO = """
            (CASE WHEN p.estado = :aprobado THEN p.monto - COALESCE(p.montoReembolsado, 0)
                  WHEN p.estado IN :devueltos THEN p.monto - COALESCE(p.montoReembolsado, p.monto)
                  ELSE 0 END)""";

    @Query("SELECT new com.LosCiruelos.padel_club_api.reservas.repository.TurnosDelPeriodo("
            + "SUM(CASE WHEN " + RESUMEN_JUGADA + " THEN 1 ELSE 0 END), "
            + "COALESCE(SUM(CASE WHEN " + RESUMEN_JUGADA + " THEN r.montoTotal ELSE 0 END), 0), "
            + "SUM(CASE WHEN " + RESUMEN_POR_JUGAR + " THEN 1 ELSE 0 END), "
            + "COALESCE(SUM(CASE WHEN " + RESUMEN_POR_JUGAR + " THEN r.montoTotal ELSE 0 END), 0), "
            + "SUM(CASE WHEN r.estado = :cancelada THEN 1 ELSE 0 END)) "
            + "FROM Reserva r "
            + "WHERE r.fechaReserva BETWEEN :desde AND :hasta")
    TurnosDelPeriodo resumenTurnos(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("hoy") LocalDate hoy,
            @Param("hora") LocalTime hora,
            @Param("juegan") Collection<EstadoReserva> juegan,
            @Param("cancelada") EstadoReserva cancelada);

    @Query("SELECT new com.LosCiruelos.padel_club_api.reservas.repository.CobrosDelPeriodo("
            + "COALESCE(SUM(CASE WHEN " + RESUMEN_JUGADA + " AND p.provider <> :bonificacion THEN " + RESUMEN_RETENIDO
            + " ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN " + RESUMEN_JUGADA + " AND p.provider = :bonificacion THEN " + RESUMEN_RETENIDO
            + " ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN " + RESUMEN_POR_JUGAR + " AND p.provider <> :bonificacion THEN " + RESUMEN_RETENIDO
            + " ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN " + RESUMEN_POR_JUGAR + " AND p.provider = :bonificacion THEN " + RESUMEN_RETENIDO
            + " ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN r.estado = :cancelada AND p.provider <> :bonificacion THEN " + RESUMEN_RETENIDO
            + " ELSE 0 END), 0)) "
            + "FROM Pago p JOIN p.reserva r "
            + "WHERE r.fechaReserva BETWEEN :desde AND :hasta")
    CobrosDelPeriodo resumenCobros(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("hoy") LocalDate hoy,
            @Param("hora") LocalTime hora,
            @Param("juegan") Collection<EstadoReserva> juegan,
            @Param("cancelada") EstadoReserva cancelada,
            @Param("aprobado") EstadoPago aprobado,
            @Param("devueltos") Collection<EstadoPago> devueltos,
            @Param("bonificacion") PaymentProvider bonificacion);

    /**
     * Cierre de caja: el monto completo de los pagos que alguna vez se cobraron (estados en
     * cobrados) con paidAt en [inicio, fin), sin bonificaciones. Lo devuelto se resta aparte,
     * por la fecha del reembolso.
     */
    @Query("""
            SELECT new com.LosCiruelos.padel_club_api.reservas.repository.EntradasDeCaja(
                   COALESCE(SUM(CASE WHEN p.provider = :efectivo THEN p.monto ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN p.provider = :mercadoPago AND p.externalPaymentId IS NOT NULL
                                     THEN p.monto ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN p.provider = :mercadoPago AND p.externalPaymentId IS NULL
                                     THEN p.monto ELSE 0 END), 0))
            FROM Pago p
            WHERE p.estado IN :cobrados
              AND p.provider <> :bonificacion
              AND p.paidAt >= :inicio AND p.paidAt < :fin
            """)
    EntradasDeCaja resumenEntradasDeCaja(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("cobrados") Collection<EstadoPago> cobrados,
            @Param("efectivo") PaymentProvider efectivo,
            @Param("mercadoPago") PaymentProvider mercadoPago,
            @Param("bonificacion") PaymentProvider bonificacion);

    /** Lo devuelto (montoReembolsado; null es el total) de los pagos DEVUELTO con reembolsadoAt en [inicio, fin). */
    @Query("""
            SELECT COALESCE(SUM(COALESCE(p.montoReembolsado, p.monto)), 0)
            FROM Pago p
            WHERE p.estado = :devuelto
              AND p.provider <> :bonificacion
              AND p.reembolsadoAt >= :inicio AND p.reembolsadoAt < :fin
            """)
    BigDecimal resumenDevoluciones(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("devuelto") EstadoPago devuelto,
            @Param("bonificacion") PaymentProvider bonificacion);

    @Query("""
            SELECT new com.LosCiruelos.padel_club_api.reservas.repository.ProductoVendido(pr.id, pr.nombre, pr.tipo,
                   SUM(rp.cantidad), SUM(rp.cantidad * rp.precioUnitario))
            FROM ReservaProducto rp JOIN rp.reserva r JOIN rp.producto pr
            WHERE r.fechaReserva BETWEEN :desde AND :hasta AND r.estado IN :estados
            GROUP BY pr.id, pr.nombre, pr.tipo
            HAVING SUM(rp.cantidad) > 0
            ORDER BY pr.id
            """)
    List<ProductoVendido> resumenProductos(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("estados") Collection<EstadoReserva> estados);
}
