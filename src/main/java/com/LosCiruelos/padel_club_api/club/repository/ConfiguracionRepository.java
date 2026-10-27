package com.LosCiruelos.padel_club_api.club.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.LosCiruelos.padel_club_api.club.entity.Configuracion;

public interface ConfiguracionRepository extends JpaRepository<Configuracion, Long> {

    default Configuracion obtener() {
        return findById(1L)
                .orElseThrow(() -> new IllegalStateException(
                        "No existe configuración del sistema. Verificá el DataInitializer."));
    }
}
