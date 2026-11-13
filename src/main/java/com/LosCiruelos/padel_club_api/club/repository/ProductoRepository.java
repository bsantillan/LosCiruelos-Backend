package com.LosCiruelos.padel_club_api.club.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.club.entity.Producto;

import jakarta.persistence.LockModeType;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    List<Producto> findAllByOrderByNombreAsc();

    List<Producto> findAllByActivoTrueAndOfrecerAlReservarTrueOrderByNombreAsc();

    List<Producto> findAllByActivoTrueOrderByIdAsc();


    boolean existsByNombreIgnoreCase(String nombre);

    Optional<Producto> findFirstByNombreIgnoreCase(String nombre);

    boolean existsByNombreIgnoreCaseAndIdNot(String nombre, Long id);

    /**
     * Bloquea los productos en orden de id. Siempre se bloquean después de las reservas
     * y en el mismo orden, para que dos operaciones no se traben entre sí.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.id IN :ids ORDER BY p.id")
    List<Producto> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.id = :id")
    Optional<Producto> findByIdForUpdate(@Param("id") Long id);

    /**
     * Si el producto se usó: en una reserva o un partido, en un turno fijo o en una venta suelta (sus
     * devoluciones cuelgan de la venta). Uno usado no se borra: se da de baja.
     */
    @Query("""
            SELECT CASE WHEN EXISTS (SELECT 1 FROM ReservaProducto rp WHERE rp.producto.id = :id)
                          OR EXISTS (SELECT 1 FROM TurnoFijoProducto tp WHERE tp.producto.id = :id)
                          OR EXISTS (SELECT 1 FROM VentaItem vi WHERE vi.producto.id = :id)
                        THEN true ELSE false END
            FROM Producto p WHERE p.id = :id
            """)
    boolean seUso(@Param("id") Long id);
}
