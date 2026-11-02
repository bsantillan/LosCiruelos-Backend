package com.LosCiruelos.padel_club_api.ventas.repository;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;

/** Lo vendido suelto de un producto en un período. */
public record VentaPorProducto(Long productoId, String nombre, TipoProducto tipo, Long cantidad, BigDecimal monto) {
}
