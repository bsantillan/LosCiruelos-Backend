package com.LosCiruelos.padel_club_api.club.dto.response;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;

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
}
