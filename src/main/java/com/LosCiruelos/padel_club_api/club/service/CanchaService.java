package com.LosCiruelos.padel_club_api.club.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.club.dto.response.CanchaResponse;
import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.Exceptions.CanchaNotFoundException;
import com.LosCiruelos.padel_club_api.club.repository.CanchaRepository;
import com.LosCiruelos.padel_club_api.mapper.CanchaMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CanchaService {

    private final CanchaRepository canchaRepository;
    private final CanchaMapper canchaMapper;

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
                .map(canchaMapper::aResponse)
                .toList();
    }
}
