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

    /**
     * Carga la cancha con lock de escritura: serializa la creación y los cambios de turno en una
     * misma cancha para que dos pedidos simultáneos no tomen el mismo horario.
     */
    public Cancha bloquear(Long id) {
        return canchaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CanchaNotFoundException("Cancha no encontrada: " + id));
    }

    /** Arranque: si no hay canchas, crea las numeradas de 1 a {@code cantidad}. @return si las creó */
    public boolean crearCanchasSiNoHay(int cantidad, String descripcion) {
        if (canchaRepository.count() > 0) {
            return false;
        }
        for (int numero = 1; numero <= cantidad; numero++) {
            canchaRepository.save(Cancha.builder().numero(numero).descripcion(descripcion).build());
        }
        return true;
    }

    public List<CanchaResponse> listarCanchas() {
        return canchaRepository.findAll().stream()
                .map(canchaMapper::aResponse)
                .toList();
    }
}
