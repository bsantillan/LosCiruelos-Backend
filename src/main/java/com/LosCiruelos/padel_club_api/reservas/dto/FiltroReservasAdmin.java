package com.LosCiruelos.padel_club_api.reservas.dto;

import java.time.LocalDate;
import java.util.Set;

import com.LosCiruelos.padel_club_api.reservas.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.TipoReserva;

/**
 * Filtros del listado de reservas del personal, ya validados. Los null no filtran.
 *
 * @param estados        estados a incluir (null: todos). Con "pendientes" ya viene intersectado
 *                       con los que deben plata: CONFIRMADA, SEÑADA y PARCIALMENTE_PAGADA (puede
 *                       quedar vacío: no hay resultados)
 * @param textoCliente   texto a buscar en nombre, apellido o email del cliente, ya normalizado
 *                       (minúsculas y sin tildes)
 */
public record FiltroReservasAdmin(
        LocalDate desde,
        LocalDate hasta,
        Set<EstadoReserva> estados,
        Long clienteId,
        String textoCliente,
        Long canchaId,
        TipoReserva tipo) {
}
