package com.LosCiruelos.padel_club_api.Services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.DTOs.Responses.CanchaResponse;
import com.LosCiruelos.padel_club_api.Entities.Cancha;
import com.LosCiruelos.padel_club_api.Exceptions.CanchaNotFoundException;
import com.LosCiruelos.padel_club_api.Repository.CanchaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CanchaService {

    private final CanchaRepository canchaRepository;

    public Cancha findByIdOrThrow(Long id) {
        return canchaRepository.findById(id)
                .orElseThrow(() -> new CanchaNotFoundException("Cancha no encontrada: " + id));
    }

    public Cancha crearCancha(Integer numero, String descripcion) {
        return canchaRepository.save(Cancha.builder()
                .numero(numero)
                .descripcion(descripcion)
                .build());
    }

    public long count() {
        return canchaRepository.count();
    }

    public List<CanchaResponse> listarCanchas() {
        return canchaRepository.findAll().stream()
                .map(CanchaResponse::from)
                .toList();
    }
}
