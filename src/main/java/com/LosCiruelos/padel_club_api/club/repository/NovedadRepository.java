package com.LosCiruelos.padel_club_api.club.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.club.entity.Novedad;

public interface NovedadRepository extends JpaRepository<Novedad, Long> {

    /** Las publicadas y vigentes (visibleHasta null o de hoy en adelante), de la más nueva a la más vieja. */
    @Query("""
            SELECT n FROM Novedad n
            WHERE n.publicada = true AND (n.visibleHasta IS NULL OR n.visibleHasta >= :hoy)
            ORDER BY n.publicadaAt DESC, n.id DESC
            """)
    List<Novedad> findVigentes(@Param("hoy") LocalDate hoy, Pageable pagina);

    /** Todas, para el panel del ADMIN, de la más nueva a la más vieja. */
    @EntityGraph(attributePaths = "creadoPor")
    Page<Novedad> findAllByOrderByCreatedAtDescIdDesc(Pageable pagina);
}
