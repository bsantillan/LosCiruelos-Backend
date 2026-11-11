package com.LosCiruelos.padel_club_api.ventas.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

import jakarta.persistence.LockModeType;

public interface VentaRepository extends JpaRepository<Venta, Long>, JpaSpecificationExecutor<Venta>, VentaConsultas {

    /** La venta con lock, para anularla o modificarla. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Venta v WHERE v.id = :id")
    Optional<Venta> findByIdForUpdate(@Param("id") Long id);

    /** Las ventas en ese estado (las que esperan el pago con QR: como mucho una). */
    List<Venta> findByEstado(EstadoVenta estado);

    @Query("SELECT v.id FROM Venta v WHERE v.orderId = :orderId")
    Optional<Long> findIdByOrderId(@Param("orderId") String orderId);

    /** Ventas que siguen esperando el pago con QR y vencieron antes de :limite: las revisa el job. */
    @Query("SELECT v.id FROM Venta v WHERE v.estado = :esperando AND v.venceAt < :limite ORDER BY v.id")
    List<Long> findIdsQrVencidas(@Param("esperando") EstadoVenta esperando, @Param("limite") LocalDateTime limite);

    /** Una venta con sus ítems y quién la registró y la anuló (el detalle). */
    @EntityGraph(attributePaths = { "items", "items.producto", "cliente", "registradoPor", "anuladaPor" })
    Optional<Venta> findConDetalleById(Long id);

    /** Ventas de esos medios en [inicio, fin) (también las anuladas), de la más vieja a la más nueva; verificada null: todas. */
    @Query("""
            SELECT v FROM Venta v
            WHERE v.medio IN :medios AND v.createdAt >= :inicio AND v.createdAt < :fin
              AND (:verificada IS NULL OR (:verificada = TRUE AND v.verificadoAt IS NOT NULL)
                   OR (:verificada = FALSE AND v.verificadoAt IS NULL))
            ORDER BY v.createdAt, v.id
            """)
    List<Venta> porMedios(@Param("medios") java.util.Collection<MedioDePago> medios, @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin, @Param("verificada") Boolean verificada);

    /** Cantidad de ventas cobradas y lo cobrado por medio en [inicio, fin), para la caja del resumen. */
    @Query("""
            SELECT new com.LosCiruelos.padel_club_api.ventas.repository.TotalesDeVentas(
                   COUNT(v),
                   COALESCE(SUM(CASE WHEN v.medio = :efectivo THEN v.total ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN v.medio = :mercadoPago THEN v.total ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN v.medio = :transferencia THEN v.total ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN v.medio = :cuentaDni THEN v.total ELSE 0 END), 0))
            FROM Venta v
            WHERE v.createdAt >= :inicio AND v.createdAt < :fin AND v.estado = :cobrada
            """)
    TotalesDeVentas totales(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("cobrada") EstadoVenta cobrada,
            @Param("efectivo") MedioDePago efectivo,
            @Param("mercadoPago") MedioDePago mercadoPago,
            @Param("transferencia") MedioDePago transferencia,
            @Param("cuentaDni") MedioDePago cuentaDni);

    /** Lo devuelto de las ventas en [inicio, fin) (por fecha de la devolución, no de la venta). */
    @Query("SELECT COALESCE(SUM(d.monto), 0) FROM VentaDevolucion d WHERE d.createdAt >= :inicio AND d.createdAt < :fin")
    BigDecimal devoluciones(@Param("inicio") LocalDateTime inicio, @Param("fin") LocalDateTime fin);

    /** Lo vendido de cada producto en [inicio, fin), en las ventas cobradas y sin lo que se devolvió. */
    @Query("""
            SELECT new com.LosCiruelos.padel_club_api.ventas.repository.VentaPorProducto(p.id, p.nombre, p.tipo,
                   SUM(i.cantidad - i.devueltas), SUM((i.cantidad - i.devueltas) * i.precioUnitario))
            FROM VentaItem i JOIN i.venta v JOIN i.producto p
            WHERE v.createdAt >= :inicio AND v.createdAt < :fin AND v.estado = :cobrada
            GROUP BY p.id, p.nombre, p.tipo
            HAVING SUM(i.cantidad - i.devueltas) > 0
            ORDER BY p.id
            """)
    List<VentaPorProducto> porProducto(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("cobrada") EstadoVenta cobrada);
}
