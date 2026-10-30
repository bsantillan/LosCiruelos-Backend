package com.LosCiruelos.padel_club_api.notificaciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.LosCiruelos.padel_club_api.notificaciones.entity.AvisosAdmin;

public interface AvisosAdminRepository extends JpaRepository<AvisosAdmin, Long> {
}
