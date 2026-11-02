package com.LosCiruelos.padel_club_api.ventas.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

/**
 * @param clienteId     null si la venta no está asociada a un cliente
 * @param registradaPor nombre de quien la registró
 */
public record VentaResponse(Long id, LocalDateTime fecha, BigDecimal total, MedioDePago medio, Long clienteId,
        String clienteNombre, String registradaPor, List<Item> items) {

    public record Item(Long productoId, String nombre, Integer cantidad, BigDecimal precioUnitario,
            BigDecimal subtotal) {
    }
}
