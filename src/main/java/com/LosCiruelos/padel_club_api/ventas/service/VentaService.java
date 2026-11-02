package com.LosCiruelos.padel_club_api.ventas.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.club.service.ProductoService;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.VentaMapper;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.ventas.dto.request.VentaRequest;
import com.LosCiruelos.padel_club_api.ventas.dto.response.VentaResponse;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Ventas sueltas que registra el personal (ADMIN o EMPLEADO). */
@Slf4j
@Service
@RequiredArgsConstructor
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoService productoService;
    private final UsuarioService usuarioService;
    private final VentaMapper ventaMapper;
    private final Reloj reloj;

    /**
     * Registra una venta suelta, cobrada en el momento. Cada ítem es un producto de venta del
     * catálogo o uno escrito a mano (nombre y precio), que queda dado de alta. Descuenta el stock
     * de los que lo controlan.
     */
    @Transactional
    public VentaResponse registrar(VentaRequest req, String emailPersonal) {
        Usuario personal = usuarioService.autenticado(emailPersonal);
        Usuario cliente = req.getClienteId() != null ? cliente(req.getClienteId()) : null;

        Venta venta = Venta.builder()
                .createdAt(reloj.ahora())
                .medio(req.getMedio())
                .cliente(cliente)
                .registradoPor(personal)
                .build();
        Map<Producto, Integer> cantidades = new LinkedHashMap<>();
        for (VentaRequest.Item item : req.getItems()) {
            cantidades.merge(producto(item), item.getCantidad(), Integer::sum);
        }
        // primero el stock (bloquea los productos y los deja actualizados), después los precios
        productoService.descontarVentaSuelta(cantidades);
        cantidades.forEach(venta::agregar);

        venta = ventaRepository.save(venta);
        log.info("Venta #{} de ${} ({}) registrada por {}", venta.getId(), venta.getTotal(), venta.getMedio(),
                emailPersonal);
        return ventaMapper.aResponse(venta);
    }

    private Producto producto(VentaRequest.Item item) {
        Producto producto;
        if (item.getProductoId() != null) {
            producto = productoService.obtener(item.getProductoId());
        } else if (item.getNombre() == null || item.getNombre().isBlank() || item.getPrecio() == null) {
            throw new ReservaException("Indicá el producto (productoId) o su nombre y precio.");
        } else {
            producto = productoService.productoManual(item.getNombre(), item.getPrecio());
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
