package com.LosCiruelos.padel_club_api.notificaciones.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.notificaciones.entity.MensajeWhatsApp;

public interface MensajeWhatsAppRepository extends JpaRepository<MensajeWhatsApp, Long> {

    /** Los mandados en [desde, hasta), agrupados por motivo, aviso y destinatario. */
    @Query("""
            SELECT new com.LosCiruelos.padel_club_api.notificaciones.repository.ConteoWhatsApp(
                       m.motivo, m.aviso, m.paraCliente, COUNT(m))
            FROM MensajeWhatsApp m
            WHERE m.enviadoAt >= :desde AND m.enviadoAt < :hasta
            GROUP BY m.motivo, m.aviso, m.paraCliente
            """)
    List<ConteoWhatsApp> contar(@Param("desde") LocalDateTime desde, @Param("hasta") LocalDateTime hasta);
}
