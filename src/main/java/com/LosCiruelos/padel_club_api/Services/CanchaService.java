package com.LosCiruelos.padel_club_api.Services;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.Entities.Cancha;
import com.LosCiruelos.padel_club_api.Repository.CanchaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CanchaService {
    
    private final CanchaRepository canchaRepository;

    public Cancha obtenerCancha(Integer canchaId) {
        return canchaRepository.findById(canchaId)
                .orElseThrow(() -> new RuntimeException("Cancha no encontrada"));
    }   
}
