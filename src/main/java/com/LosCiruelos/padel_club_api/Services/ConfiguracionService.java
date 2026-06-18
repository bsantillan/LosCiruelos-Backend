package com.LosCiruelos.padel_club_api.Services;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.Repository.ConfiguracionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    private final ConfiguracionRepository configuracionRepository;

    @Cacheable("configuracion")          // se cachea en memoria la primera vez
    public Configuracion obtener() {
        return configuracionRepository.obtener();
    }

    @CacheEvict(value = "configuracion", allEntries = true)  // limpia caché al actualizar
    public Configuracion actualizar(Configuracion nueva) {
        nueva.setId(1L);                
        return configuracionRepository.save(nueva);
    }
}
