package com.LosCiruelos.padel_club_api.ventas.service;

import com.LosCiruelos.padel_club_api.club.service.JornadaService;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.club.service.ProductoService;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.ventas.exception.VentaNoEncontradaException;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.VentaMapper;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.ventas.dto.request.DevolucionVentaRequest;
import com.LosCiruelos.padel_club_api.ventas.dto.request.VentaRequest;
import com.LosCiruelos.padel_club_api.ventas.dto.response.VentaResponse;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.VentaDevolucion;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ventas sueltas que registra el personal (ADMIN o EMPLEADO). Una venta no se edita ni se borra: se
 * anula, y solo se puede cambiar el cliente asociado. Toda operación sobre una venta la bloquea primero.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoService productoService;
    private final UsuarioService usuarioService;
    private final VentaMapper ventaMapper;
    private final Reloj reloj;
    private final CobroQrDeVentas cobroQr;
    private final JornadaService jornadaService;

    /**
     * Registra una venta suelta. Cada ítem es un producto de venta del catálogo o uno escrito a mano
     * (nombre y precio), que queda dado de alta. Descuenta el stock de los que lo controlan. En
     * efectivo o con Cuenta DNI queda cobrada; con Mercado Pago se le asigna a la caja el QR por el
     * total y queda ESPERANDO_PAGO (con el stock apartado) hasta que MP confirma el pago.
     */
    @Transactional
    public VentaResponse registrar(VentaRequest req, String emailPersonal) {
        Usuario personal = usuarioService.autenticado(emailPersonal);
        Usuario cliente = req.getClienteId() != null ? cliente(req.getClienteId()) : null;
        boolean conQr = req.getMedio() == MedioDePago.MERCADO_PAGO;
        if (conQr) {
            cobroQr.exigirConfigurado();
            cobroQr.ocuparCaja(); // primero la caja, después el stock
        }

        Venta venta = Venta.builder()
                .createdAt(reloj.ahora())
                .medio(req.getMedio())
                .cliente(cliente)
                .registradoPor(personal)
                .build();
        if (conQr) {
            venta.esperarPagoConQr(cobroQr.vencimiento());
        }
        Map<Producto, Integer> cantidades = new LinkedHashMap<>();
        // un producto escrito a mano se da de alta con stock igual a todo lo que se vende de él
        Map<String, Integer> manuales = new java.util.HashMap<>();
        req.getItems().stream().filter(i -> i.getProductoId() == null && i.getNombre() != null)
                .forEach(i -> manuales.merge(i.getNombre().trim().toLowerCase(), i.getCantidad(), Integer::sum));
        for (VentaRequest.Item item : req.getItems()) {
            cantidades.merge(producto(item, manuales), item.getCantidad(), Integer::sum);
        }
        // primero el stock (bloquea los productos y los deja actualizados), después los precios
        productoService.descontarVentaSuelta(cantidades);
        cantidades.forEach(venta::agregar);

        venta = ventaRepository.save(venta);
        if (conQr) {
            // si el proveedor falla, la transacción se revierte entera (también el stock)
            cobroQr.asignar(venta);
        }
        log.info("Venta #{} de ${} ({}) registrada por {}", venta.getId(), venta.getTotal(), venta.getMedio(),
                emailPersonal);
        return ventaMapper.aResponse(venta);
    }

    /** Solo ADMIN: marca (o desmarca) una venta con Cuenta DNI como verificada en la cuenta del club. */
    @Transactional
    public VentaResponse verificar(Long ventaId, boolean verificada, String email) {
        Usuario admin = usuarioService.autenticado(email);
        Roles.exigirAdmin(admin, "Solo el ADMIN puede verificar una venta.");
        Venta venta = bloquear(ventaId);
        venta.verificar(verificada, admin.getId(), reloj.ahora());
        log.info("Venta #{} con Cuenta DNI {} por {}", ventaId, verificada ? "verificada" : "sin verificar", email);
        return ventaMapper.aResponse(venta);
    }

    /**
     * Deja la venta sin efecto (error de carga, el cliente no pagó, se cobró por otro medio): deja de
     * contar en la caja y el stock vuelve. El ADMIN anula cualquier venta; el EMPLEADO, solo las de
     * hoy sin verificar. Queda quién, cuándo y por qué. Si se cobró con el QR, Mercado Pago devuelve
     * el total (si no lo acepta, 502 y no se anula); si no, la plata se devuelve a mano por el mismo medio.
     */
    @Transactional
    public VentaResponse anular(Long ventaId, String motivo, String email) {
        Usuario personal = usuarioService.autenticado(email);
        Venta venta = bloquear(ventaId);
        venta.validarQuePuedeAnularla(personal, jornadaService.actual());
        venta.anular(personal, motivo.trim(), reloj.ahora());
        if (venta.seCobroConQr()) {
            cobroQr.reembolsar(venta, venta.getTotal(), "anulacion");
        }
        productoService.reponerVentaSuelta(venta.cantidades());
        log.info("Venta #{} de ${} ({}) anulada por {}: {}", ventaId, venta.getTotal(), venta.getMedio(), email,
                venta.getMotivoAnulacion());
        return ventaMapper.aResponse(venta);
    }

    /**
     * Solo ADMIN: el cliente devuelve todo o parte de lo que compró. Queda la devolución (fecha, quién,
     * motivo y monto), que cuenta en la caja de hoy; vuelve al stock lo que sirve. Si se cobró con el
     * QR, Mercado Pago devuelve el monto (si no lo acepta, 502 y no se registra); si no, la plata se
     * devuelve a mano por el mismo medio.
     */
    @Transactional
    public VentaResponse devolver(Long ventaId, DevolucionVentaRequest req, String email) {
        Usuario admin = usuarioService.autenticado(email);
        Roles.exigirAdmin(admin, "Solo el ADMIN puede registrar una devolución.");
        Venta venta = bloquear(ventaId);
        VentaDevolucion devolucion = venta.devolver(req.getItems().stream()
                .map(i -> new VentaDevolucion.Pedido(i.getProductoId(), i.getCantidad(), i.getVuelveAlStock()))
                .toList(), req.getMotivo().trim(), admin, reloj.ahora());
        if (venta.seCobroConQr()) {
            cobroQr.reembolsar(venta, devolucion.getMonto(), "devolucion-" + venta.getDevoluciones().size());
        }
        Map<Producto, Integer> aReponer = devolucion.aReponer();
        if (!aReponer.isEmpty()) {
            productoService.reponerVentaSuelta(aReponer);
        }
        ventaRepository.flush(); // la respuesta lleva el id de la devolución
        log.info("Venta #{}: devolución #{} de ${} ({}) por {}: {}", ventaId, devolucion.getId(), devolucion.getMonto(),
                venta.getMedio(), email, devolucion.getMotivo());
        return ventaMapper.aResponse(venta);
    }

    /** Cambia el cliente asociado (null: venta al público). El EMPLEADO, solo en las ventas de hoy. */
    @Transactional
    public VentaResponse cambiarCliente(Long ventaId, Long clienteId, String email) {
        Usuario personal = usuarioService.autenticado(email);
        Venta venta = bloquear(ventaId);
        if (!Roles.esAdmin(personal) && !venta.esDeLaJornada(jornadaService.actual())) {
            throw new PermisoDenegadoException("Solo podés modificar ventas de hoy.");
        }
        venta.cambiarCliente(clienteId != null ? cliente(clienteId) : null);
        log.info("Venta #{}: cliente {} por {}", ventaId, clienteId, email);
        return ventaMapper.aResponse(venta);
    }

    /** La venta con lock (404 si no existe). */
    public Venta bloquear(Long ventaId) {
        return ventaRepository.findByIdForUpdate(ventaId).orElseThrow(() -> new VentaNoEncontradaException(ventaId));
    }

    /** @param manuales unidades que se venden de cada producto escrito a mano (nombre en minúsculas) */
    private Producto producto(VentaRequest.Item item, Map<String, Integer> manuales) {
        Producto producto;
        if (item.getProductoId() != null) {
            producto = productoService.obtener(item.getProductoId());
        } else if (item.getNombre() == null || item.getNombre().isBlank() || item.getPrecio() == null) {
            throw new ReservaException("Indicá el producto (productoId) o su nombre y precio.");
        } else {
            producto = productoService.productoManual(item.getNombre(), item.getPrecio(),
                    manuales.get(item.getNombre().trim().toLowerCase()));
        }
        if (producto.getTipo() != TipoProducto.VENTA) {
            throw new ReservaException(producto.getNombre() + " se alquila con un turno: no se vende suelto.");
        }
        return producto;
    }

    private Usuario cliente(Long clienteId) {
        Usuario cliente = usuarioService.findByIdOrThrow(clienteId);
        if (cliente.getRol() != Role.CLIENTE) {
            throw new ReservaException("El usuario indicado no es un cliente.");
        }
        return cliente;
    }
}
