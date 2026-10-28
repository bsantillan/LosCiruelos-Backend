package com.LosCiruelos.padel_club_api.reportes.repository;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;

/** Unidades y monto de un producto pedido en las reservas, con el precio congelado en cada una. */
public record ProductoVendido(Long productoId, String nombre, TipoProducto tipo, Long cantidad, BigDecimal monto) {
}
