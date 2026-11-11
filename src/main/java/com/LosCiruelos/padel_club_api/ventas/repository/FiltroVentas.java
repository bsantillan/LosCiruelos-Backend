package com.LosCiruelos.padel_club_api.ventas.repository;

import java.time.LocalDate;
import java.util.Set;

import com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

/**
 * Filtros del listado de ventas del personal, ya validados. Los null no filtran.
 *
 * @param textoCliente nombre, apellido o email del cliente, ya normalizado (minúsculas y sin tildes)
 * @param productoId   las ventas que tienen ese producto
 */
public record FiltroVentas(
        LocalDate desde,
        LocalDate hasta,
        Long productoId,
        String textoCliente,
        Long clienteId,
        Set<MedioDePago> medios,
        Set<EstadoVenta> estados) {
}
