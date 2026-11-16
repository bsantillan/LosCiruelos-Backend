package com.LosCiruelos.padel_club_api.club.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.LosCiruelos.padel_club_api.club.dto.request.NovedadRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.NovedadPublicaResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.NovedadResponse;
import com.LosCiruelos.padel_club_api.club.entity.Novedad;
import com.LosCiruelos.padel_club_api.club.exception.NovedadNoEncontradaException;
import com.LosCiruelos.padel_club_api.club.repository.NovedadRepository;
import com.LosCiruelos.padel_club_api.common.PaginaResponse;
import com.LosCiruelos.padel_club_api.common.Paginacion;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.archivos.AlmacenDeImagenes;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.NovedadMapper;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Las novedades del club: las publica el ADMIN y se ven en la página de inicio. */
@Slf4j
@Service
@RequiredArgsConstructor
public class NovedadService {

    public static final int LIMITE_POR_DEFECTO = 6;
    public static final int LIMITE_MAXIMO = 20;
    public static final int TAMANIO_POR_DEFECTO = 20;
    public static final int TAMANIO_MAXIMO = 100;

    private final NovedadRepository novedadRepository;
    private final NovedadMapper mapper;
    private final UsuarioService usuarioService;
    private final AlmacenDeImagenes almacen;
    private final Reloj reloj;

    /** Las publicadas y vigentes, de la más nueva a la más vieja (público). */
    @Transactional(readOnly = true)
    public List<NovedadPublicaResponse> vigentes(Integer limite) {
        int cuantas = limite != null ? limite : LIMITE_POR_DEFECTO;
        if (cuantas < 1 || cuantas > LIMITE_MAXIMO) {
            throw new ReservaException("El límite tiene que ser de 1 a " + LIMITE_MAXIMO + ".");
        }
        return novedadRepository.findVigentes(reloj.hoy(), PageRequest.of(0, cuantas)).stream()
                .map(mapper::aPublica).toList();
    }

    /** Todas (ADMIN), con su estado, de la más nueva a la más vieja. */
    @Transactional(readOnly = true)
    public PaginaResponse<NovedadResponse> listar(int pagina, Integer tamanio) {
        PageRequest pageable = Paginacion.pagina(pagina, tamanio != null ? tamanio : TAMANIO_POR_DEFECTO,
                TAMANIO_MAXIMO, org.springframework.data.domain.Sort.unsorted());
        return PaginaResponse.de(novedadRepository.findAllByOrderByCreatedAtDescIdDesc(pageable)
                .map(n -> mapper.aResponse(n, reloj.hoy())));
    }

    /** Nace como borrador. */
    @Transactional
    public NovedadResponse crear(NovedadRequest req, String email) {
        LocalDateTime ahora = reloj.ahora();
        Novedad novedad = Novedad.builder().creadoPor(usuarioService.autenticado(email)).createdAt(ahora)
                .updatedAt(ahora).publicada(false).build();
        copiar(req, novedad);
        novedad = novedadRepository.save(novedad);
        log.info("Novedad #{} creada por {}", novedad.getId(), email);
        return mapper.aResponse(novedad, reloj.hoy());
    }

    @Transactional
    public NovedadResponse actualizar(Long id, NovedadRequest req) {
        Novedad novedad = buscar(id);
        copiar(req, novedad);
        novedad.setUpdatedAt(reloj.ahora());
        return mapper.aResponse(novedad, reloj.hoy());
    }

    @Transactional
    public NovedadResponse publicar(Long id, boolean publicada) {
        Novedad novedad = buscar(id);
        novedad.publicar(publicada, reloj.ahora());
        log.info("Novedad #{} {}", id, publicada ? "publicada" : "despublicada");
        return mapper.aResponse(novedad, reloj.hoy());
    }

    /** Borra la novedad y su imagen. */
    @Transactional
    public void eliminar(Long id) {
        Novedad novedad = buscar(id);
        almacen.borrarAlConfirmar(novedad.getImagen());
        novedadRepository.delete(novedad);
        log.info("Novedad #{} eliminada", id);
    }

    /** Pone (o reemplaza) la imagen: JPG, PNG o WEBP de hasta 5 MB. */
    @Transactional
    public NovedadResponse ponerImagen(Long id, MultipartFile archivo) {
        Novedad novedad = buscar(id);
        String anterior = novedad.getImagen();
        novedad.setImagen(almacen.guardar(archivo).nombre());
        novedad.setUpdatedAt(reloj.ahora());
        almacen.borrarAlConfirmar(anterior);
        return mapper.aResponse(novedad, reloj.hoy());
    }

    @Transactional
    public NovedadResponse sacarImagen(Long id) {
        Novedad novedad = buscar(id);
        almacen.borrarAlConfirmar(novedad.getImagen());
        novedad.setImagen(null);
        novedad.setUpdatedAt(reloj.ahora());
        return mapper.aResponse(novedad, reloj.hoy());
    }

    private Novedad buscar(Long id) {
        return novedadRepository.findById(id).orElseThrow(() -> new NovedadNoEncontradaException(id));
    }

    private static void copiar(NovedadRequest req, Novedad novedad) {
        novedad.setTitulo(req.getTitulo().trim());
        novedad.setDescripcion(req.getDescripcion().trim());
        novedad.setEtiqueta(req.getEtiqueta() == null || req.getEtiqueta().isBlank() ? null : req.getEtiqueta().trim());
        novedad.setVisibleHasta(req.getVisibleHasta());
    }
}
