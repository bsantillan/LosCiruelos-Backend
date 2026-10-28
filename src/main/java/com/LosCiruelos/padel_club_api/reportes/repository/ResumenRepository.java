package com.LosCiruelos.padel_club_api.reportes.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.common.ConsultasDeTurno;

/** Consultas agregadas del resumen del club: todo se suma en la base, sin traer reservas a memoria. */
public interface ResumenRepository extends Repository<Reserva, Long> {

    String TERMINADO = ConsultasDeTurno.TERMINADO;

    /** Reserva confirmada (se juega o se jugó) con el turno terminado. */
    String JUGADA = "r.estado IN :juegan AND " + TERMINADO;

    /** Reserva confirmada con el turno por terminar. */
    String POR_JUGAR = "r.estado IN :juegan AND NOT " + TERMINADO;

    /**
     * Lo que el club se queda de un pago, igual que Pago.getMontoRetenido: aprobado menos lo
     * devuelto; de un pago devuelto (o por devolver), lo que no se devolvió; el resto, nada.
     */
    String RETENIDO = """
            (CASE WHEN p.estado = :aprobado THEN p.monto - COALESCE(p.montoReembolsado, 0)
                  WHEN p.estado IN :devueltos THEN p.monto - COALESCE(p.montoReembolsado, p.monto)
                  ELSE 0 END)""";

    @Query("SELECT new com.LosCiruelos.padel_club_api.reportes.repository.TurnosDelPeriodo("
            + "SUM(CASE WHEN " + JUGADA + " THEN 1 ELSE 0 END), "
            + "COALESCE(SUM(CASE WHEN " + JUGADA + " THEN r.montoTotal ELSE 0 END), 0), "
            + "SUM(CASE WHEN " + POR_JUGAR + " THEN 1 ELSE 0 END), "
            + "COALESCE(SUM(CASE WHEN " + POR_JUGAR + " THEN r.montoTotal ELSE 0 END), 0), "
            + "SUM(CASE WHEN r.estado = :cancelada THEN 1 ELSE 0 END)) "
            + "FROM Reserva r "
            + "WHERE r.fechaReserva BETWEEN :desde AND :hasta")
    TurnosDelPeriodo turnos(
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta,
            @Param("hoy") LocalDate hoy,
            @Param("hora") LocalTime hora,
            @Param("juegan") Collection<EstadoReserva> juegan,
            @Param("cancelada") EstadoReserva cancelada);

    @Query("SELECT new com.LosCiruelos.padel_club_api.reportes.repository.CobrosDelPeriodo("
            + "COALESCE(SUM(CASE WHEN " + JUGADA + " AND p.provider <> :bonificacion THEN " + RETENIDO
            + " ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN " + JUGADA + " AND p.provider = :bonificacion THEN " + RETENIDO
            + " ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN " + POR_JUGAR + " AND p.provider <> :bonificacion THEN " + RETENIDO
            + " ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN " + POR_JUGAR + " AND p.provider = :bonificacion THEN " + RETENIDO
            + " ELSE 0 END), 0), "
            + "COALESCE(SUM(CASE WHEN r.estado = :cancelada AND p.provider <> :bonificacion THEN " + RETENIDO
            + " ELSE 0 END), 0)) "
            + "FROM Pago p JOIN p.reserva r "
            + "WHERE r.fechaReserva BETWEEN :desde AND :hasta")
    CobrosDelPeriodo cobros(
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
            SELECT new com.LosCiruelos.padel_club_api.reportes.repository.EntradasDeCaja(
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
    EntradasDeCaja entradasDeCaja(
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
    BigDecimal devoluciones(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("devuelto") EstadoPago devuelto,
            @Param("bonificacion") PaymentProvider bonificacion);

    @Query("""
            SELECT new com.LosCiruelos.padel_club_api.reportes.repository.ProductoVendido(pr.id, pr.nombre, pr.tipo,
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
