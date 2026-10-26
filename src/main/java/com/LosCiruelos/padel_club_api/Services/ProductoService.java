package com.LosCiruelos.padel_club_api.Services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.reservas.DisponibilidadService;
import com.LosCiruelos.padel_club_api.reservas.dto.UsoAlquiler;
import com.LosCiruelos.padel_club_api.DTOs.Requests.ProductoRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ProductoDisponibleResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ProductoResponse;
import com.LosCiruelos.padel_club_api.Entities.Producto;
import com.LosCiruelos.padel_club_api.reservas.Reserva;
import com.LosCiruelos.padel_club_api.reservas.ReservaProducto;
import com.LosCiruelos.padel_club_api.reservas.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.TipoProducto;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ProductoNoEncontradoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Exceptions.StockInsuficienteException;
import com.LosCiruelos.padel_club_api.Repository.ProductoRepository;
import com.LosCiruelos.padel_club_api.reservas.ReservaProductoRepository;
import com.LosCiruelos.padel_club_api.common.Reloj;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Catálogo de productos (sección Stock de la configuración) y movimientos de stock
 * de las reservas.
 *
 * VENTA: el stock baja al reservar y vuelve si la reserva se cancela o expira.
 * ALQUILER: el stock es el total de unidades; se controla el máximo en uso al mismo
 * tiempo en el horario pedido, sumando todas las canchas.
 *
 * Regla de locks: los productos se bloquean siempre después de las reservas y en
 * orden de id, todos en un solo paso ({@link #moverStock}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ReservaProductoRepository reservaProductoRepository;
    private final EntityManager entityManager;
    private final Reloj reloj;

    // ── Catálogo ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ProductoResponse> listarActivos() {
        return productoRepository.findAllByActivoTrueOrderByNombreAsc().stream().map(ProductoResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> listarTodos() {
        return productoRepository.findAllByOrderByNombreAsc().stream().map(ProductoResponse::from).toList();
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
        return ProductoResponse.from(productoRepository.save(producto));
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
        return ProductoResponse.from(productoRepository.save(producto));
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
        return ProductoResponse.from(productoRepository.save(producto));
    }

    /** Baja lógica: deja de ofrecerse, pero se conserva en las reservas que ya lo tienen. */
    @Transactional
    public ProductoResponse desactivar(Long id) {
        Producto producto = bloquearProducto(id);
        producto.setActivo(false);
        return ProductoResponse.from(productoRepository.save(producto));
    }

    /** Unidades que se pueden pedir de cada producto activo para un horario. */
    @Transactional(readOnly = true)
    public List<ProductoDisponibleResponse> disponibilidad(LocalDate fecha, LocalTime inicio, LocalTime fin) {
        if (!inicio.isBefore(fin)) {
            throw new ReservaException("La hora de inicio debe ser anterior a la hora de fin.");
        }
        LocalDateTime ahora = reloj.ahora();
        return productoRepository.findAllByActivoTrueOrderByNombreAsc().stream()
                .map(p -> new ProductoDisponibleResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getTipo(),
                        p.getPrecio(), disponibles(p, fecha, inicio, fin, null, ahora)))
                .toList();
    }

    // ── Stock de reservas ────────────────────────────────────────────────

    /**
     * Mueve el stock en un solo paso, con todos los productos involucrados bloqueados en orden de id:
     * <ol>
     * <li>devuelve el stock de VENTA de {@code aLiberar} (reservas que se cancelan o expiran);</li>
     * <li>si hay {@code destino}, devuelve el stock de sus productos actuales y los reemplaza por
     * {@code pedido} (código de producto → cantidad), validando el stock disponible.</li>
     * </ol>
     * Las reservas de {@code aLiberar} y {@code destino} tienen que estar bloqueadas de antes.
     */
    public void moverStock(Collection<Reserva> aLiberar, Reserva destino, Map<String, Integer> pedido,
            LocalDateTime ahora) {
        Map<String, Integer> cantidades = new TreeMap<>();
        pedido.forEach((codigo, cantidad) -> {
            if (cantidad != null && cantidad > 0) {
                cantidades.put(codigo, cantidad);
            }
        });

        // 1. Ids de todos los productos involucrados, para bloquearlos juntos y en orden
        TreeSet<Long> ids = new TreeSet<>();
        aLiberar.forEach(r -> r.getProductos().forEach(i -> ids.add(i.getProducto().getId())));
        if (destino != null) {
            destino.getProductos().forEach(i -> ids.add(i.getProducto().getId()));
        }
        List<Producto> pedidos = cantidades.isEmpty() ? List.of() : productoRepository.findByCodigoIn(cantidades.keySet());
        pedidos.forEach(p -> ids.add(p.getId()));
        if (ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            // refresh con lock: SELECT ... FOR UPDATE y valores actualizados aunque ya estuviera en memoria
            entityManager.refresh(entityManager.find(Producto.class, id), LockModeType.PESSIMISTIC_WRITE);
        }

        // 2. Devolver lo que ya no se usa
        aLiberar.forEach(this::devolverStockDeVenta);
        if (destino == null) {
            return;
        }
        devolverStockDeVenta(destino);
        // los ítems que se siguen pidiendo se actualizan en el lugar (borrar y recrear choca con
        // UNIQUE (reserva_id, producto_id) porque Hibernate inserta antes de borrar)
        destino.getProductos().removeIf(i -> !cantidades.containsKey(i.getProducto().getCodigo()));

        // 3. Tomar lo pedido
        for (Map.Entry<String, Integer> entrada : cantidades.entrySet()) {
            Producto producto = pedidos.stream()
                    .filter(p -> p.getCodigo().equals(entrada.getKey()))
                    .findFirst()
                    .orElseThrow(() -> new ReservaException("El producto " + entrada.getKey() + " no existe."));
            tomar(destino, producto, entrada.getValue(), ahora);
        }
    }

    /** Devuelve el stock de VENTA de una reserva que se cancela o expira (la reserva ya está bloqueada). */
    public void liberar(Reserva reserva) {
        moverStock(List.of(reserva), null, Map.of(), reloj.ahora());
    }

    private void tomar(Reserva destino, Producto producto, int cantidad, LocalDateTime ahora) {
        if (!Boolean.TRUE.equals(producto.getActivo())) {
            throw new ReservaException(producto.getNombre() + " no está disponible por el momento.");
        }
        int disponibles = disponibles(producto, destino.getFechaReserva(), destino.getHoraInicio(),
                destino.getHoraFin(), destino.getId(), ahora);
        if (cantidad > disponibles) {
            String unidades = disponibles == 1 ? "1 unidad" : disponibles + " unidades";
            throw new StockInsuficienteException(producto.getTipo() == TipoProducto.VENTA
                    ? "%s: hay %s en stock.".formatted(producto.getNombre(), unidades)
                    : "%s: hay %s disponibles para ese horario.".formatted(producto.getNombre(), unidades));
        }
        if (producto.getTipo() == TipoProducto.VENTA) {
            producto.setStock(producto.getStock() - cantidad);
        }

        ReservaProducto item = destino.getProductos().stream()
                .filter(i -> i.getProducto().getId().equals(producto.getId()))
                .findFirst()
                .orElseGet(() -> {
                    ReservaProducto nuevo = ReservaProducto.builder().reserva(destino).producto(producto).build();
                    destino.getProductos().add(nuevo);
                    return nuevo;
                });
        item.setCantidad(cantidad);
        item.setPrecioUnitario(producto.getPrecio());
    }

    private void devolverStockDeVenta(Reserva reserva) {
        for (ReservaProducto item : reserva.getProductos()) {
            Producto producto = item.getProducto();
            if (producto.getTipo() == TipoProducto.VENTA) {
                producto.setStock(producto.getStock() + item.getCantidad());
            }
        }
    }

    /**
     * Registra la devolución de un alquiler (la reserva ya está bloqueada). Las unidades que
     * faltan se descuentan del stock total del producto.
     */
    public void registrarDevolucion(ReservaProducto item, int devueltas, LocalDateTime ahora) {
        Producto producto = item.getProducto();
        entityManager.refresh(producto, LockModeType.PESSIMISTIC_WRITE);

        int faltantes = item.getCantidad() - devueltas;
        if (faltantes > 0) {
            producto.setStock(Math.max(0, producto.getStock() - faltantes));
            log.warn("Faltan {} {} de la reserva #{}: el stock baja a {}", faltantes, producto.getNombre(),
                    item.getReserva().getId(), producto.getStock());
        }
        item.setCantidadDevuelta(devueltas);
        item.setDevueltaAt(ahora);
    }

    private int disponibles(Producto producto, LocalDate fecha, LocalTime inicio, LocalTime fin,
            Long excluirReservaId, LocalDateTime ahora) {
        if (producto.getTipo() == TipoProducto.VENTA) {
            return producto.getStock();
        }
        // alquiler: total − máximo en uso en ese horario − las de turnos terminados que no volvieron
        List<UsoAlquiler> usos = reservaProductoRepository.findAlquileresQueSeSuperponen(producto.getId(),
                excluirReservaId != null ? excluirReservaId : -1L, fecha, inicio, fin, ahora,
                DisponibilidadService.ESTADOS_INACTIVOS, EstadoReserva.PENDIENTE, DisponibilidadService.PAGOS_ACTIVOS);
        Long sinDevolver = reservaProductoRepository.sumarSinDevolver(producto.getId(), Reserva.ESTADOS_QUE_SE_JUEGAN,
                ahora.toLocalDate(), ahora.toLocalTime());
        int afuera = sinDevolver != null ? sinDevolver.intValue() : 0;
        return Math.max(0, producto.getStock() - usoMaximo(usos, inicio, fin) - afuera);
    }

    /**
     * Máximo de unidades en uso al mismo tiempo dentro de [desde, hasta).
     * Ej.: 2 paletas de 18:00 a 19:30 y 2 de 19:30 a 21:00 nunca se usan juntas, así que
     * para 18:30–20:00 el máximo es 2, no 4.
     */
    static int usoMaximo(List<UsoAlquiler> usos, LocalTime desde, LocalTime hasta) {
        TreeMap<LocalTime, Integer> cambios = new TreeMap<>();
        for (UsoAlquiler uso : usos) {
            LocalTime inicio = uso.horaInicio().isAfter(desde) ? uso.horaInicio() : desde;
            LocalTime fin = uso.horaFin().isBefore(hasta) ? uso.horaFin() : hasta;
            if (inicio.isBefore(fin)) {
                cambios.merge(inicio, uso.cantidad(), Integer::sum);
                cambios.merge(fin, -uso.cantidad(), Integer::sum);
            }
        }
        int enUso = 0;
        int maximo = 0;
        for (int cambio : new ArrayList<>(cambios.values())) {
            enUso += cambio;
            maximo = Math.max(maximo, enUso);
        }
        return maximo;
    }

    private Producto bloquearProducto(Long id) {
        return productoRepository.findByIdForUpdate(id).orElseThrow(() -> new ProductoNoEncontradoException(id));
    }
}
