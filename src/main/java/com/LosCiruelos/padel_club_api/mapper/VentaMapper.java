package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.ventas.dto.response.VentaResponse;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;

/** Venta suelta → VentaResponse. */
@Component
public class VentaMapper {

    public VentaResponse aResponse(Venta venta) {
        Usuario cliente = venta.getCliente();
        return new VentaResponse(venta.getId(), venta.getCreatedAt(), Dinero.redondear(venta.getTotal()),
                venta.getMedio(), cliente != null ? cliente.getId() : null,
                cliente != null ? nombre(cliente) : null, nombre(venta.getRegistradoPor()),
                venta.getItems().stream()
                        .map(i -> new VentaResponse.Item(i.getProducto().getId(), i.getProducto().getNombre(),
                                i.getCantidad(), Dinero.redondear(i.getPrecioUnitario()),
                                Dinero.redondear(i.getSubtotal())))
                        .toList());
    }

    private static String nombre(Usuario usuario) {
        return (usuario.getNombre() + " " + usuario.getApellido()).trim();
    }
}
