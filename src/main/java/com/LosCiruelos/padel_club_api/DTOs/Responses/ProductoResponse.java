package com.LosCiruelos.padel_club_api.DTOs.Responses;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.Entities.Producto;
import com.LosCiruelos.padel_club_api.Entities.Enum.TipoProducto;

import lombok.Data;

@Data
public class ProductoResponse {
    private Long id;
    // PELOTAS / PALETAS para los productos del sistema; null para el resto
    private String codigo;
    private String nombre;
    private String descripcion;
    private TipoProducto tipo;
    private BigDecimal precio;
    private Integer stock;
    private Boolean activo;

    public static ProductoResponse from(Producto producto) {
        ProductoResponse r = new ProductoResponse();
        r.setId(producto.getId());
        r.setCodigo(producto.getCodigo());
        r.setNombre(producto.getNombre());
        r.setDescripcion(producto.getDescripcion());
        r.setTipo(producto.getTipo());
        r.setPrecio(producto.getPrecio());
        r.setStock(producto.getStock());
        r.setActivo(producto.getActivo());
        return r;
    }
}
