package com.LosCiruelos.padel_club_api.reservas.service;

import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import java.math.BigDecimal;
import com.LosCiruelos.padel_club_api.reservas.dto.request.ProductoPedidoRequest;
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

import com.LosCiruelos.padel_club_api.reservas.dto.response.ProductoDisponibleResponse;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.club.exception.StockInsuficienteException;
import com.LosCiruelos.padel_club_api.club.service.ProductoService;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaProducto;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.UsoAlquiler;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stock de los productos que se piden en las reservas.
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
public class StockService {

    private final ProductoService productoService;
    private final ReservaRepository reservaRepository;
    private final EntityManager entityManager;
    private final Reloj reloj;

    /** Unidades que se pueden pedir de cada producto que se ofrece al reservar, para un horario. */
    @Transactional(readOnly = true)
    public List<ProductoDisponibleResponse> disponibilidad(LocalDate fecha, LocalTime inicio, LocalTime fin) {
        validarHorario(inicio, fin);
        LocalDateTime ahora = reloj.ahora();
        return productoService.paraReservar().stream()
                .map(p -> disponibilidadDe(p, fecha, inicio, fin, null, Map.of(), ahora))
                .toList();
    }

    /**
     * Para el personal: unidades libres de todos los productos activos (se ofrezcan o no al reservar),
     * en orden de id, con el mismo cálculo que valida el stock al guardar. Con {@code excluirReservaId}
     * (al editar una reserva o un partido) lo que esa reserva ya tiene cuenta como libre: sus alquileres
     * no se cuentan como en uso y sus unidades de venta vuelven al stock, como hace {@link #moverStock}.
     * Si la reserva no existe, se ignora.
     */
    @Transactional(readOnly = true)
    public List<ProductoDisponibleResponse> disponibilidadParaPersonal(LocalDate fecha, LocalTime inicio,
            LocalTime fin, Long excluirReservaId) {
        validarHorario(inicio, fin);
        LocalDateTime ahora = reloj.ahora();
        Map<Long, Integer> deLaReserva = new TreeMap<>();
        Long excluir = null;
        Reserva reserva = excluirReservaId != null ? reservaRepository.findById(excluirReservaId).orElse(null) : null;
        if (reserva != null) {
            excluir = reserva.getId();
            // una cancelada o vencida ya devolvió su stock de venta: no se cuenta dos veces
            if (!reserva.estaInactiva()) {
                reserva.getProductos().forEach(i -> deLaReserva.merge(i.getProducto().getId(), i.getCantidad(),
                        Integer::sum));
            }
        }
        Long excluida = excluir;
        return productoService.activos().stream()
                .map(p -> disponibilidadDe(p, fecha, inicio, fin, excluida, deLaReserva, ahora))
                .toList();
    }

    private static void validarHorario(LocalTime inicio, LocalTime fin) {
        if (!inicio.isBefore(fin)) {
            throw new ReservaException("La hora de inicio debe ser anterior a la hora de fin.");
        }
    }

    /** @param deLaReserva unidades de cada producto que tiene la reserva que se está editando */
    private ProductoDisponibleResponse disponibilidadDe(Producto p, LocalDate fecha, LocalTime inicio, LocalTime fin,
            Long excluirReservaId, Map<Long, Integer> deLaReserva, LocalDateTime ahora) {
        Integer libres = null;
        if (p.llevaStock()) {
            libres = disponibles(p, fecha, inicio, fin, excluirReservaId, ahora);
            if (p.getTipo() == TipoProducto.VENTA) {
                libres += deLaReserva.getOrDefault(p.getId(), 0);
            }
        }
        return new ProductoDisponibleResponse(p.getId(), p.getCodigo(), p.getNombre(), p.getTipo(), p.getPrecio(),
                libres, p.seOfreceAlReservar());
    }

    /**
     * Mueve el stock en un solo paso, con todos los productos involucrados bloqueados en orden de id:
     * <ol>
     * <li>devuelve el stock de VENTA de {@code aLiberar} (reservas que se cancelan o expiran);</li>
     * <li>si hay {@code destino}, devuelve el stock de sus productos actuales y los reemplaza por
     * {@code pedido} (id de producto → cantidad), validando el stock disponible.</li>
     * </ol>
     * Las reservas de {@code aLiberar} y {@code destino} tienen que estar bloqueadas de antes.
     */
    public void moverStock(Collection<Reserva> aLiberar, Reserva destino, Map<Long, Integer> pedido,
            LocalDateTime ahora) {
        Map<Long, Integer> cantidades = new TreeMap<>();
        pedido.forEach((productoId, cantidad) -> {
            if (cantidad != null && cantidad > 0) {
                cantidades.put(productoId, cantidad);
            }
        });

        // 1. Ids de todos los productos involucrados, para bloquearlos juntos y en orden
        TreeSet<Long> ids = new TreeSet<>();
        aLiberar.forEach(r -> r.getProductos().forEach(i -> ids.add(i.getProducto().getId())));
        if (destino != null) {
            destino.getProductos().forEach(i -> ids.add(i.getProducto().getId()));
        }
        List<Producto> pedidos = productosDe(cantidades);
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
        destino.getProductos().removeIf(i -> !cantidades.containsKey(i.getProducto().getId()));

        // 3. Tomar lo pedido
        for (Map.Entry<Long, Integer> entrada : cantidades.entrySet()) {
            Producto producto = pedidos.stream()
                    .filter(p -> p.getId().equals(entrada.getKey()))
                    .findFirst()
                    .orElseThrow();
            tomar(destino, producto, entrada.getValue(), ahora);
        }
    }

    /** Lo pedido en una reserva, un partido o un turno fijo: id de producto → cantidad (los repetidos se suman). */
    public static Map<Long, Integer> pedido(List<ProductoPedidoRequest> productos) {
        Map<Long, Integer> pedido = new TreeMap<>();
        if (productos != null) {
            productos.forEach(p -> pedido.merge(p.getProductoId(), p.getCantidad(), Integer::sum));
        }
        return pedido;
    }

    /** Los productos de un pedido; si alguno no existe, 400. */
    public List<Producto> productosDe(Map<Long, Integer> pedido) {
        if (pedido.isEmpty()) {
            return List.of();
        }
        List<Producto> productos = productoService.buscarPorIds(pedido.keySet());
        pedido.keySet().stream()
                .filter(id -> productos.stream().noneMatch(p -> p.getId().equals(id)))
                .findFirst()
                .ifPresent(id -> {
                    throw new ReservaException("El producto " + id + " no existe.");
                });
        return productos;
    }

    /** El cliente solo pide al reservar los productos que se ofrecen (el resto los agrega el personal). */
    public void exigirQueSeOfrezcan(Map<Long, Integer> pedido) {
        productosDe(pedido).stream()
                .filter(p -> !p.seOfreceAlReservar())
                .findFirst()
                .ifPresent(p -> {
                    throw new ReservaException(p.getNombre() + " no se puede pedir al reservar: pedíselo al club.");
                });
    }

    /**
     * El producto que suma el personal: uno del catálogo o, si no hay {@code productoId}, uno escrito
     * a mano (nombre y precio), que queda dado de alta.
     */
    public Producto productoParaAgregar(Long productoId, String nombre, BigDecimal precio) {
        if (productoId != null) {
            return productoService.obtener(productoId);
        }
        if (nombre == null || nombre.isBlank() || precio == null) {
            throw new ReservaException("Indicá el producto (productoId) o su nombre y precio.");
        }
        return productoService.productoManual(nombre, precio);
    }

    /**
     * Suma unidades de un producto a la reserva (ya bloqueada), con el producto bloqueado. Los que ya
     * tenía mantienen su precio; uno nuevo toma el precio de hoy.
     */
    public void agregar(Reserva reserva, Producto producto, int cantidad, LocalDateTime ahora) {
        entityManager.refresh(producto, LockModeType.PESSIMISTIC_WRITE);
        if (!Boolean.TRUE.equals(producto.getActivo())) {
            throw new ReservaException(producto.getNombre() + " no está disponible por el momento.");
        }
        ReservaProducto item = reserva.getProductos().stream()
                .filter(i -> i.getProducto().getId().equals(producto.getId()))
                .findFirst()
                .orElse(null);
        int yaPedidas = item != null ? item.getCantidad() : 0;
        if (producto.llevaStock()) {
            // venta: el stock ya tiene descontado lo de esta reserva; alquiler: cuenta el total en uso
            int disponibles = disponibles(producto, reserva.getFechaReserva(), reserva.getHoraInicio(),
                    reserva.getHoraFin(), reserva.getId(), ahora);
            int necesarias = producto.getTipo() == TipoProducto.VENTA ? cantidad : yaPedidas + cantidad;
            if (necesarias > disponibles) {
                String unidades = disponibles == 1 ? "1 unidad" : disponibles + " unidades";
                throw new StockInsuficienteException(producto.getTipo() == TipoProducto.VENTA
                        ? "%s: hay %s en stock.".formatted(producto.getNombre(), unidades)
                        : "%s: hay %s disponibles para ese horario.".formatted(producto.getNombre(), unidades));
            }
            if (producto.getTipo() == TipoProducto.VENTA) {
                producto.setStock(producto.getStock() - cantidad);
            }
        }
        if (item == null) {
            reserva.getProductos().add(ReservaProducto.builder().reserva(reserva).producto(producto)
                    .cantidad(cantidad).precioUnitario(producto.getPrecio()).build());
        } else {
            item.setCantidad(yaPedidas + cantidad);
        }
    }

    /**
     * Saca unidades de un producto de la reserva (ya bloqueada); null saca todas. El stock de venta
     * vuelve. No se puede sacar un alquiler que ya tiene la devolución registrada.
     */
    public void quitar(Reserva reserva, Long productoId, Integer cantidad) {
        ReservaProducto item = reserva.getProductos().stream()
                .filter(i -> i.getProducto().getId().equals(productoId))
                .findFirst()
                .orElseThrow(() -> new ReservaException("La reserva no tiene el producto " + productoId + "."));
        int aSacar = cantidad != null ? cantidad : item.getCantidad();
        if (aSacar < 1 || aSacar > item.getCantidad()) {
            throw new ReservaException("Se pidieron %d: se pueden sacar entre 1 y %d."
                    .formatted(item.getCantidad(), item.getCantidad()));
        }
        if (item.devolucionRegistrada()) {
            throw new EstadoInvalidoException("La devolución de " + item.getProducto().getNombre()
                    + " ya fue registrada: no se puede sacar.");
        }
        Producto producto = item.getProducto();
        entityManager.refresh(producto, LockModeType.PESSIMISTIC_WRITE);
        if (producto.getTipo() == TipoProducto.VENTA && producto.llevaStock()) {
            producto.setStock(producto.getStock() + aSacar);
        }
        if (aSacar == item.getCantidad()) {
            reserva.getProductos().remove(item);
        } else {
            item.setCantidad(item.getCantidad() - aSacar);
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
        int disponibles = producto.llevaStock() ? disponibles(producto, destino.getFechaReserva(),
                destino.getHoraInicio(), destino.getHoraFin(), destino.getId(), ahora) : Integer.MAX_VALUE;
        if (cantidad > disponibles) {
            String unidades = disponibles == 1 ? "1 unidad" : disponibles + " unidades";
            throw new StockInsuficienteException(producto.getTipo() == TipoProducto.VENTA
                    ? "%s: hay %s en stock.".formatted(producto.getNombre(), unidades)
                    : "%s: hay %s disponibles para ese horario.".formatted(producto.getNombre(), unidades));
        }
        if (producto.getTipo() == TipoProducto.VENTA && producto.llevaStock()) {
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
            if (producto.getTipo() == TipoProducto.VENTA && producto.llevaStock()) {
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
        List<UsoAlquiler> usos = reservaRepository.findAlquileresQueSeSuperponen(producto.getId(),
                excluirReservaId != null ? excluirReservaId : -1L, fecha, inicio, fin, ahora,
                DisponibilidadService.ESTADOS_INACTIVOS, EstadoReserva.PENDIENTE, DisponibilidadService.PAGOS_ACTIVOS);
        Long sinDevolver = reservaRepository.sumarSinDevolver(producto.getId(), Reserva.ESTADOS_QUE_SE_JUEGAN,
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
}
