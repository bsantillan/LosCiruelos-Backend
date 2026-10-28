package com.LosCiruelos.padel_club_api.club.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.Config.AppConfig.ConfiguracionCache;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;

import lombok.RequiredArgsConstructor;

/**
 * Configuración del club (registro único id = 1), cacheada. Las reglas que se derivan de ella
 * (horario de cada día, duración válida, precio y seña) están en {@link Configuracion}.
 */
@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    private final ConfiguracionRepository configuracionRepository;

    @Cacheable(ConfiguracionCache.NOMBRE)
    public Configuracion obtener() {
        return configuracionRepository.obtener();
    }

    /** Arranque: guarda la configuración inicial si todavía no hay ninguna. @return si la creó */
    @CacheEvict(value = ConfiguracionCache.NOMBRE, allEntries = true)
    public boolean crearSiNoExiste(Configuracion inicial) {
        if (configuracionRepository.existsById(1L)) {
            return false;
        }
        inicial.setId(1L);
        configuracionRepository.save(inicial);
        return true;
    }

    @CacheEvict(value = ConfiguracionCache.NOMBRE, allEntries = true)
    public Configuracion actualizar(Configuracion nueva) {
        nueva.setId(1L);
        return configuracionRepository.save(nueva);
    }
}
