package com.LosCiruelos.padel_club_api.club.service;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.club.dto.request.ProductoRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.ProductoResponse;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ProductoNoEncontradoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Exceptions.StockInsuficienteException;
import com.LosCiruelos.padel_club_api.club.repository.ProductoRepository;
import com.LosCiruelos.padel_club_api.mapper.ProductoMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Catálogo de productos (sección Stock de la configuración). El stock que toman y devuelven las
 * reservas lo maneja {@link com.LosCiruelos.padel_club_api.reservas.service.StockService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ProductoMapper productoMapper;

    // ── Catálogo ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProductoResponse> listarActivos() {
        return productoRepository.findAllByActivoTrueOrderByNombreAsc().stream().map(productoMapper::aResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listarTodos() {
        return productoRepository.findAllByOrderByNombreAsc().stream().map(productoMapper::aResponse).toList();
    }

    @Transactional
    public ProductoResponse crear(ProductoRequest req) {
        if (productoRepository.existsByNombreIgnoreCase(req.getNombre().trim())) {
            throw new EstadoInvalidoException("Ya existe un producto llamado " + req.getNombre().trim() + ".");
        }
        Producto producto = Producto.builder()
                .nombre(req.getNombre().trim())
                .descripcion(req.getDescripcion())
                .tipo(req.getTipo())
                .precio(req.getPrecio())
                .stock(req.getStock())
                .activo(req.getActivo() == null || req.getActivo())
                .build();
        return productoMapper.aResponse(productoRepository.save(producto));
    }

    @Transactional
    public ProductoResponse actualizar(Long id, ProductoRequest req) {
        Producto producto = bloquearProducto(id);
        if (req.getTipo() != producto.getTipo()) {
            throw new ReservaException("No se puede cambiar el tipo de un producto; creá uno nuevo.");
        }
        if (productoRepository.existsByNombreIgnoreCaseAndIdNot(req.getNombre().trim(), id)) {
            throw new EstadoInvalidoException("Ya existe un producto llamado " + req.getNombre().trim() + ".");
        }
        producto.setNombre(req.getNombre().trim());
        producto.setDescripcion(req.getDescripcion());
        producto.setPrecio(req.getPrecio());
        producto.setStock(req.getStock());
        if (req.getActivo() != null) {
            producto.setActivo(req.getActivo());
        }
        log.info("Producto #{} actualizado: precio ${}, stock {}", id, producto.getPrecio(), producto.getStock());
        return productoMapper.aResponse(productoRepository.save(producto));
    }

    /** Suma o resta unidades al stock (reposición, rotura). El stock no puede quedar negativo. */
    @Transactional
    public ProductoResponse ajustarStock(Long id, int ajuste) {
        Producto producto = bloquearProducto(id);
        int nuevo = producto.getStock() + ajuste;
        if (nuevo < 0) {
            throw new StockInsuficienteException(
                    "El stock de %s no puede quedar negativo (hay %d).".formatted(producto.getNombre(), producto.getStock()));
        }
        producto.setStock(nuevo);
        log.info("Stock de {} ajustado en {}: queda {}", producto.getNombre(), ajuste, nuevo);
        return productoMapper.aResponse(productoRepository.save(producto));
    }

    /** Baja lógica: deja de ofrecerse, pero se conserva en las reservas que ya lo tienen. */
    @Transactional
    public ProductoResponse desactivar(Long id) {
        Producto producto = bloquearProducto(id);
        producto.setActivo(false);
        return productoMapper.aResponse(productoRepository.save(producto));
    }

    /** Productos activos (los que se pueden pedir en una reserva). */
    public List<Producto> activos() {
        return productoRepository.findAllByActivoTrueOrderByNombreAsc();
    }

    /** Productos con esos códigos (PELOTAS, PALETAS). */
    public List<Producto> buscarPorCodigos(Collection<String> codigos) {
        return productoRepository.findByCodigoIn(codigos);
    }

    private Producto bloquearProducto(Long id) {
        return productoRepository.findByIdForUpdate(id).orElseThrow(() -> new ProductoNoEncontradoException(id));
    }
}
