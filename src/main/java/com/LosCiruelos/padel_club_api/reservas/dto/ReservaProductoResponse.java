package com.LosCiruelos.padel_club_api.reservas.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.reservas.ReservaProducto;
import com.LosCiruelos.padel_club_api.Entities.Enum.TipoProducto;

import lombok.Data;

@Data
public class ReservaProductoResponse {
    private Long productoId;
    private String codigo;
    private String nombre;
    private TipoProducto tipo;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
    // alquileres: unidades devueltas (null = todavía no se registró la devolución)
    private Integer cantidadDevuelta;
    private LocalDateTime devueltaAt;

    public static ReservaProductoResponse from(ReservaProducto item) {
        ReservaProductoResponse r = new ReservaProductoResponse();
        r.setProductoId(item.getProducto().getId());
        r.setCodigo(item.getProducto().getCodigo());
        r.setNombre(item.getProducto().getNombre());
        r.setTipo(item.getProducto().getTipo());
        r.setCantidad(item.getCantidad());
        r.setPrecioUnitario(item.getPrecioUnitario());
        r.setSubtotal(item.getSubtotal());
        r.setCantidadDevuelta(item.getCantidadDevuelta());
        r.setDevueltaAt(item.getDevueltaAt());
        return r;
    }
}
