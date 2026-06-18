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

    public Cancha findByIdOrThrow(Integer canchaId) {
        return canchaRepository.findById(canchaId)
                .orElseThrow(() -> new RuntimeException("Cancha no encontrada"));
    }

    public Cancha crearCancha(Integer numero, String descripcion) {
        Cancha cancha = Cancha.builder()
                .numero(numero)
                .descripccion(descripcion)
                .build();
        return canchaRepository.save(cancha);
    }

    public Cancha actualizarCancha(Integer canchaId, Integer numero, String descripcion) {
        Cancha cancha = findByIdOrThrow(canchaId);
        cancha.setNumero(numero);
        cancha.setDescripccion(descripcion);
        return canchaRepository.save(cancha);
    }

    public void eliminarCancha(Integer canchaId) {
        Cancha cancha = findByIdOrThrow(canchaId);
        canchaRepository.delete(cancha);
        
    }

    public long count() {
        return canchaRepository.count();
    }

    public Cancha findById(Integer canchaId) {
        return canchaRepository.findById(canchaId).orElse(null);
    }
}
