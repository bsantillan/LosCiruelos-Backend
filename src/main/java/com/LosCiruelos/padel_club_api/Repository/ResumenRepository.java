package com.LosCiruelos.padel_club_api.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.DTOs.Resumen.CobrosPorEstado;
import com.LosCiruelos.padel_club_api.DTOs.Resumen.ProductoVendido;
import com.LosCiruelos.padel_club_api.DTOs.Resumen.ReservasPorEstado;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;

/** Consultas agregadas del resumen del club: todo se suma en la base, sin traer reservas a memoria. */
public interface ResumenRepository extends Repository<Reserva, Long> {

    /**
     * Lo que el club se queda de un pago, igual que Pago.getMontoRetenido: aprobado menos lo
     * devuelto; de un pago devuelto (o por devolver), lo que no se devolvió; el resto, nada.
     */
    String RETENIDO = """
            (CASE WHEN p.estado = :aprobado THEN p.monto - COALESCE(p.montoReembolsado, 0)
                  WHEN p.estado IN :devueltos THEN p.monto - COALESCE(p.montoReembolsado, p.monto)
                  ELSE 0 END)""";

    @Query("SELECT new com.LosCiruelos.padel_club_api.DTOs.Resumen.CobrosPorEstado(r.estado, "
            + "COALESCE(SUM(CASE WHEN p.externalPaymentId IS NOT NULL THEN " + RETENIDO + " ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN p.externalPaymentId IS NULL THEN " + RETENIDO + " ELSE 0 END), 0)) "
            + "FROM Pago p JOIN p.reserva r "
            + "WHERE r.fechaReserva BETWEEN :desde AND :hasta "
            + "GROUP BY r.estado")
    List<CobrosPorEstado> cobrosPorEstado(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("aprobado") EstadoPago aprobado,
            @Param("devueltos") Collection<EstadoPago> devueltos);

    @Query("""
            SELECT new com.LosCiruelos.padel_club_api.DTOs.Resumen.ReservasPorEstado(r.estado, COUNT(r),
                   COALESCE(SUM(r.montoTotal), 0),
                   SUM(CASE WHEN r.fechaReserva < :hoy OR (r.fechaReserva = :hoy AND r.horaFin <= :hora)
                            THEN 1 ELSE 0 END))
            FROM Reserva r
            WHERE r.fechaReserva BETWEEN :desde AND :hasta
            GROUP BY r.estado
            """)
    List<ReservasPorEstado> reservasPorEstado(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("hoy") LocalDate hoy,
            @Param("hora") LocalTime hora);

    @Query("""
            SELECT new com.LosCiruelos.padel_club_api.DTOs.Resumen.ProductoVendido(pr.id, pr.nombre, pr.tipo,
                   SUM(rp.cantidad), SUM(rp.cantidad * rp.precioUnitario))
            FROM ReservaProducto rp JOIN rp.reserva r JOIN rp.producto pr
            WHERE r.fechaReserva BETWEEN :desde AND :hasta AND r.estado IN :estados
            GROUP BY pr.id, pr.nombre, pr.tipo
            HAVING SUM(rp.cantidad) > 0
            ORDER BY pr.id
            """)
    List<ProductoVendido> productos(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("estados") Collection<EstadoReserva> estados);
}
