package com.LosCiruelos.padel_club_api.club.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.LosCiruelos.padel_club_api.club.dto.request.CanchaRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.CanchaResponse;
import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.club.exception.CanchaNotFoundException;
import com.LosCiruelos.padel_club_api.club.repository.CanchaRepository;
import com.LosCiruelos.padel_club_api.common.archivos.AlmacenDeImagenes;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.mapper.CanchaMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * La gestión de canchas del ADMIN: alta, edición, baja y reactivación, borrado de una que nunca se usó
 * y sus fotos. La baja con reservas o turnos fijos la arma {@link BajaDeCanchas} (varias transacciones).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CanchaAdminService {

    private final CanchaRepository canchaRepository;
    private final CanchaMapper canchaMapper;
    private final HorariosOcupados horariosOcupados;
    private final AlmacenDeImagenes almacen;

    @Transactional(readOnly = true)
    public List<CanchaResponse> listar() {
        return canchaRepository.findAllByOrderByNumeroAsc().stream()
                .map(c -> canchaMapper.aResponseAdmin(c, !horariosOcupados.canchaUsada(c.getId())))
                .toList();
    }

    @Transactional
    public CanchaResponse crear(CanchaRequest req) {
        if (canchaRepository.existsByNumero(req.getNumero())) {
            throw new EstadoInvalidoException("Ya existe la cancha " + req.getNumero() + ".");
        }
        Cancha cancha = new Cancha();
        cancha.setActiva(true);
        copiar(req, cancha);
        cancha = canchaRepository.save(cancha);
        log.info("Cancha #{} (número {}) creada", cancha.getId(), cancha.getNumero());
        return canchaMapper.aResponseAdmin(cancha, true);
    }

    @Transactional
    public CanchaResponse actualizar(Long id, CanchaRequest req) {
        Cancha cancha = buscar(id);
        if (canchaRepository.existsByNumeroAndIdNot(req.getNumero(), id)) {
            throw new EstadoInvalidoException("Ya existe la cancha " + req.getNumero() + ".");
        }
        copiar(req, cancha);
        return aResponse(cancha);
    }

    /** Da de baja la cancha (no cancela nada: eso lo hace {@link BajaDeCanchas} antes, si se confirmó). */
    @Transactional
    public CanchaResponse desactivar(Long id) {
        Cancha cancha = bloquearCualquiera(id);
        cancha.setActiva(false);
        log.info("Cancha #{} (número {}) dada de baja", id, cancha.getNumero());
        return aResponse(cancha);
    }

    @Transactional
    public CanchaResponse reactivar(Long id) {
        Cancha cancha = buscar(id);
        cancha.setActiva(true);
        log.info("Cancha #{} (número {}) reactivada", id, cancha.getNumero());
        return aResponse(cancha);
    }

    /**
     * Borrado definitivo de una cancha que nunca se usó (con sus fotos del disco).
     *
     * @throws EstadoInvalidoException (409) si tiene reservas, partidos o turnos fijos
     */
    @Transactional
    public void eliminar(Long id) {
        Cancha cancha = buscar(id);
        if (horariosOcupados.canchaUsada(id)) {
            throw new EstadoInvalidoException("La cancha tiene reservas o turnos fijos: desactivala en lugar de eliminarla.");
        }
        cancha.getFotos().forEach(f -> almacen.borrarAlConfirmar(f.getArchivo()));
        canchaRepository.delete(cancha);
        log.info("Cancha #{} (número {}) eliminada", id, cancha.getNumero());
    }

    // ── Fotos ────────────────────────────────────────────────────────────

    @Transactional
    public CanchaResponse agregarFoto(Long id, MultipartFile archivo) {
        Cancha cancha = buscar(id);
        cancha.exigirLugarParaOtraFoto(); // antes de guardar el archivo
        AlmacenDeImagenes.Imagen imagen = almacen.guardar(archivo);
        cancha.agregarFoto(imagen.nombre());
        canchaRepository.saveAndFlush(cancha);
        return aResponse(cancha);
    }

    @Transactional
    public CanchaResponse sacarFoto(Long id, Long fotoId) {
        Cancha cancha = buscar(id);
        almacen.borrarAlConfirmar(cancha.sacarFoto(fotoId));
        return aResponse(cancha);
    }

    @Transactional
    public CanchaResponse ordenarFotos(Long id, List<Long> fotos) {
        Cancha cancha = buscar(id);
        cancha.ordenarFotos(fotos);
        return aResponse(cancha);
    }

    // ── Auxiliares ───────────────────────────────────────────────────────

    private CanchaResponse aResponse(Cancha cancha) {
        return canchaMapper.aResponseAdmin(cancha, !horariosOcupados.canchaUsada(cancha.getId()));
    }

    private Cancha buscar(Long id) {
        return canchaRepository.findById(id).orElseThrow(() -> new CanchaNotFoundException("Cancha no encontrada: " + id));
    }

    private Cancha bloquearCualquiera(Long id) {
        return canchaRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CanchaNotFoundException("Cancha no encontrada: " + id));
    }

    private static void copiar(CanchaRequest req, Cancha cancha) {
        cancha.setNumero(req.getNumero());
        cancha.setNombre(vacioANull(req.getNombre()));
        cancha.setDescripcion(vacioANull(req.getDescripcion()));
        cancha.setTipo(req.getTipo());
        cancha.setSuperficie(vacioANull(req.getSuperficie()));
        cancha.setIluminacion(req.getIluminacion());
    }

    private static String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
