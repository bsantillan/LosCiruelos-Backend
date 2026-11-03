package com.LosCiruelos.padel_club_api.ventas.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    /** Las ventas de [inicio, fin), de la más nueva a la más vieja, con sus ítems y quién las registró. */
    @EntityGraph(attributePaths = { "items", "items.producto", "cliente", "registradoPor" })
    List<Venta> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(LocalDateTime inicio,
            LocalDateTime fin);

    /** Ventas de un medio en [inicio, fin), de la más vieja a la más nueva; verificada null: todas. */
    @Query("""
            SELECT v FROM Venta v
            WHERE v.medio = :medio AND v.createdAt >= :inicio AND v.createdAt < :fin
              AND (:verificada IS NULL OR (:verificada = TRUE AND v.verificadoAt IS NOT NULL)
                   OR (:verificada = FALSE AND v.verificadoAt IS NULL))
            ORDER BY v.createdAt, v.id
            """)
    List<Venta> porMedio(@Param("medio") MedioDePago medio, @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin, @Param("verificada") Boolean verificada);

    /** Cantidad de ventas y lo cobrado por medio en [inicio, fin), para la caja del resumen. */
    @Query("""
            SELECT new com.LosCiruelos.padel_club_api.ventas.repository.TotalesDeVentas(
                   COUNT(v),
                   COALESCE(SUM(CASE WHEN v.medio = :efectivo THEN v.total ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN v.medio = :mercadoPago THEN v.total ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN v.medio = :cuentaDni THEN v.total ELSE 0 END), 0))
            FROM Venta v
            WHERE v.createdAt >= :inicio AND v.createdAt < :fin
            """)
    TotalesDeVentas totales(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin,
            @Param("efectivo") MedioDePago efectivo,
            @Param("mercadoPago") MedioDePago mercadoPago,
            @Param("cuentaDni") MedioDePago cuentaDni);

    /** Lo vendido de cada producto en [inicio, fin). */
    @Query("""
            SELECT new com.LosCiruelos.padel_club_api.ventas.repository.VentaPorProducto(p.id, p.nombre, p.tipo,
                   SUM(i.cantidad), SUM(i.cantidad * i.precioUnitario))
            FROM VentaItem i JOIN i.venta v JOIN i.producto p
            WHERE v.createdAt >= :inicio AND v.createdAt < :fin
            GROUP BY p.id, p.nombre, p.tipo
            ORDER BY p.id
            """)
    List<VentaPorProducto> porProducto(
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin);
}
