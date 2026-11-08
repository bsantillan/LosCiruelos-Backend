package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.ventas.dto.response.MedioDeVentaResponse;
import com.LosCiruelos.padel_club_api.ventas.dto.response.VentaResponse;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.VentaDevolucion;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

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
                                Dinero.redondear(i.getSubtotal()), i.getDevueltas()))
                        .toList(),
                venta.getMedio() == MedioDePago.CUENTA_DNI ? venta.estaVerificada() : null, venta.getVerificadoAt(),
                venta.getEstado(), venta.getAnuladaAt(),
                venta.getAnuladaPor() != null ? nombre(venta.getAnuladaPor()) : null, venta.getMotivoAnulacion(),
                Dinero.redondear(venta.montoDevuelto()),
                venta.getDevoluciones().stream().map(VentaMapper::devolucion).toList(),
                venta.seCobroConQr() ? new VentaResponse.Qr(venta.getOrderId(), venta.getVenceAt()) : null);
    }

    /** @param qrConfigurado si el QR de la caja está configurado (los medios con QR dependen de eso) */
    public MedioDeVentaResponse aMedio(MedioDePago medio, boolean qrConfigurado) {
        boolean disponible = !medio.conQr() || qrConfigurado;
        return new MedioDeVentaResponse(medio, medio.etiqueta(), disponible, medio.conQr(),
                disponible ? null : "El QR de la caja de Mercado Pago no está configurado.");
    }

    private static VentaResponse.Devolucion devolucion(VentaDevolucion d) {
        return new VentaResponse.Devolucion(d.getId(), d.getCreatedAt(), nombre(d.getRegistradaPor()), d.getMotivo(),
                Dinero.redondear(d.getMonto()),
                d.getItems().stream()
                        .map(i -> new VentaResponse.ItemDevuelto(i.getVentaItem().getProducto().getId(),
                                i.getVentaItem().getProducto().getNombre(), i.getCantidad(), i.getVuelveAlStock()))
                        .toList());
    }

    private static String nombre(Usuario usuario) {
        return (usuario.getNombre() + " " + usuario.getApellido()).trim();
    }
}
