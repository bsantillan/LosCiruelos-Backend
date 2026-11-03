package com.LosCiruelos.padel_club_api.club.service;

import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.club.dto.request.ProductoRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.ProductoResponse;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.club.exception.ProductoNoEncontradoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.club.exception.StockInsuficienteException;
import com.LosCiruelos.padel_club_api.club.repository.ProductoRepository;
import com.LosCiruelos.padel_club_api.mapper.ProductoMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
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
    private final EntityManager entityManager;

    // ── Catálogo ─────────────────────────────────────────────────────────

    /** El catálogo del cliente: los activos que se ofrecen al reservar. */
    @Transactional(readOnly = true)
    public List<ProductoResponse> listarParaReservar() {
        return paraReservar().stream().map(productoMapper::aResponse).toList();
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
                .ofrecerAlReservar(Boolean.TRUE.equals(req.getOfrecerAlReservar()))
                .controlaStock(req.getControlaStock() == null || req.getControlaStock())
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
        if (req.getOfrecerAlReservar() != null) {
            producto.setOfrecerAlReservar(req.getOfrecerAlReservar());
        }
        if (req.getControlaStock() != null) {
            producto.setControlaStock(req.getControlaStock());
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

    /** Los activos que se ofrecen al reservar (los que ve y pide el cliente). */
    public List<Producto> paraReservar() {
        return productoRepository.findAllByActivoTrueAndOfrecerAlReservarTrueOrderByNombreAsc();
    }

    /** Todos los activos, en orden de id: los que puede pedir el personal (se ofrezcan o no al reservar). */
    public List<Producto> activos() {
        return productoRepository.findAllByActivoTrueOrderByIdAsc();
    }

    /** El producto con ese id; si no existe, 404. */
    public Producto obtener(Long id) {
        return productoRepository.findById(id).orElseThrow(() -> new ProductoNoEncontradoException(id));
    }

    /**
     * Un producto que el personal escribe a mano (en una reserva o una venta suelta): si ya hay uno
     * con ese nombre se usa ese; si no, queda dado de alta como producto de venta, sin control de
     * stock y sin ofrecerse al reservar (el ADMIN después lo puede editar).
     */
    @Transactional
    public Producto productoManual(String nombre, BigDecimal precio) {
        String limpio = nombre.trim();
        return productoRepository.findFirstByNombreIgnoreCase(limpio).orElseGet(() -> {
            Producto nuevo = productoRepository.save(Producto.builder()
                    .nombre(limpio)
                    .tipo(TipoProducto.VENTA)
                    .precio(precio)
                    .stock(0)
                    .activo(true)
                    .ofrecerAlReservar(false)
                    .controlaStock(false)
                    .build());
            log.info("Producto #{} {} dado de alta a mano (${})", nuevo.getId(), limpio, precio);
            return nuevo;
        });
    }

    /**
     * Saca del stock lo de una venta suelta (producto → unidades). Bloquea los productos en orden de
     * id y con sus valores actualizados; los que no controlan stock no se descuentan.
     */
    @Transactional
    public void descontarVentaSuelta(Map<Producto, Integer> cantidades) {
        Map<Long, Producto> porId = new TreeMap<>();
        cantidades.keySet().forEach(p -> porId.put(p.getId(), p));
        for (Producto producto : porId.values()) {
            // refresh con lock: SELECT ... FOR UPDATE y valores actualizados aunque ya estuviera en memoria
            entityManager.refresh(producto, LockModeType.PESSIMISTIC_WRITE);
        }
        for (Producto producto : porId.values()) {
            if (!Boolean.TRUE.equals(producto.getActivo())) {
                throw new ReservaException(producto.getNombre() + " no está disponible por el momento.");
            }
            int cantidad = cantidades.get(producto);
            if (!producto.llevaStock()) {
                continue;
            }
            if (cantidad > producto.getStock()) {
                String unidades = producto.getStock() == 1 ? "1 unidad" : producto.getStock() + " unidades";
                throw new StockInsuficienteException("%s: hay %s en stock.".formatted(producto.getNombre(), unidades));
            }
            producto.setStock(producto.getStock() - cantidad);
        }
    }

    /** Productos con esos ids (los que no existen no vienen). */
    public List<Producto> buscarPorIds(Collection<Long> ids) {
        return productoRepository.findAllById(ids);
    }

    private Producto bloquearProducto(Long id) {
        return productoRepository.findByIdForUpdate(id).orElseThrow(() -> new ProductoNoEncontradoException(id));
    }
}
