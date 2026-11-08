package com.LosCiruelos.padel_club_api.reservas.repository;

import java.time.LocalDate;
import java.util.Set;

import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.TipoReserva;

/**
 * Filtros del listado de reservas del personal, ya validados. Los null no filtran.
 *
 * @param estados        estados a incluir (null: todos). Con "pendientes" ya viene intersectado
 *                       con los que deben plata: CONFIRMADA, SEÑADA y PARCIALMENTE_PAGADA (puede
 *                       quedar vacío: no hay resultados)
 * @param textoCliente   texto a buscar en nombre, apellido o email del cliente, ya normalizado
 *                       (minúsculas y sin tildes)
 * @param turnoFijoId    solo los partidos de ese turno fijo (también las fechas dadas de baja)
 * @param deTurnoFijo    true: solo las que salen de un turno fijo; false: solo las que no
 */
public record FiltroReservas(
        LocalDate desde,
        LocalDate hasta,
        Set<EstadoReserva> estados,
        Long clienteId,
        String textoCliente,
        Long canchaId,
        TipoReserva tipo,
        Long turnoFijoId,
        Boolean deTurnoFijo) {
}
