package com.LosCiruelos.padel_club_api.club.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.club.dto.response.CanchaResponse;
import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.club.exception.CanchaNotFoundException;
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
        Cancha cancha = canchaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CanchaNotFoundException("Cancha no encontrada: " + id));
        if (!cancha.estaActiva()) {
            throw new com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException(
                    "La cancha " + cancha.getNumero() + " está dada de baja: no acepta reservas, partidos ni turnos fijos.");
        }
        return cancha;
    }

    /** Una cancha activa (para la disponibilidad pública: una dada de baja no existe). */
    public Cancha activaOrThrow(Long id) {
        return canchaRepository.findById(id).filter(Cancha::estaActiva)
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

    /** Las canchas activas con sus fotos (GET /canchas: la grilla, Reservar y la página de inicio). */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<CanchaResponse> listarCanchas() {
        return canchaRepository.findByActivaTrueOrderByNumeroAsc().stream()
                .map(canchaMapper::aResponse)
                .toList();
    }
}
