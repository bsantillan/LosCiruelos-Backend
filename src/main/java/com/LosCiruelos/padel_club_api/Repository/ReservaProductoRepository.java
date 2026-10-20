package com.LosCiruelos.padel_club_api.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.DTOs.UsoAlquiler;
import com.LosCiruelos.padel_club_api.Entities.ReservaProducto;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;

public interface ReservaProductoRepository extends JpaRepository<ReservaProducto, Long> {

    /**
     * Alquileres del producto en reservas de cualquier cancha que se superponen con
     * [horaInicio, horaFin). Cuentan las reservas que ocupan su turno: no CANCELADA ni
     * EXPIRADA, salvo PENDIENTE vencidas sin pagos activos (mismo criterio que las canchas).
     * Los alquileres ya devueltos no cuentan.
     */
    @Query("""
            SELECT new com.LosCiruelos.padel_club_api.DTOs.UsoAlquiler(
                       r.horaInicio, r.horaFin, rp.cantidad)
            FROM ReservaProducto rp JOIN rp.reserva r
            WHERE rp.producto.id = :productoId
              AND rp.cantidadDevuelta IS NULL
              AND r.id <> :excluirReservaId
              AND r.fechaReserva = :fecha
              AND r.horaInicio < :horaFin
              AND r.horaFin > :horaInicio
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
              AND (r.fechaReserva < :hoy OR (r.fechaReserva = :hoy AND r.horaFin <= :hora))
            """)
    Long sumarSinDevolver(
            @Param("productoId") Long productoId,
            @Param("estadosQueSeJuegan") Collection<EstadoReserva> estadosQueSeJuegan,
            @Param("hoy") LocalDate hoy,
            @Param("hora") LocalTime hora);
}
