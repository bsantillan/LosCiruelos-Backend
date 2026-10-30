package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.club.dto.response.ProductoResponse;
import com.LosCiruelos.padel_club_api.club.entity.Producto;

/** Producto → ProductoResponse. */
@Component
public class ProductoMapper {

    public ProductoResponse aResponse(Producto producto) {
        ProductoResponse response = new ProductoResponse();
        response.setId(producto.getId());
        response.setCodigo(producto.getCodigo());
        response.setNombre(producto.getNombre());
        response.setDescripcion(producto.getDescripcion());
        response.setTipo(producto.getTipo());
        response.setPrecio(producto.getPrecio());
        response.setStock(producto.getStock());
        response.setActivo(producto.getActivo());
        return response;
    }
}
