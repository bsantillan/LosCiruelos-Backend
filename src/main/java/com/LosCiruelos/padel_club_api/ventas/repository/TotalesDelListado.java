package com.LosCiruelos.padel_club_api.ventas.repository;

import java.math.BigDecimal;
import java.util.Map;

import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

/**
 * Totales de todo el filtro del listado de ventas (no solo de la página).
 *
 * @param cobradoPorMedio total de las ventas COBRADA de cada medio (sin restar lo devuelto)
 * @param devueltoPorMedio lo devuelto de esas ventas, por medio
 */
public record TotalesDelListado(long cobradas, long anuladas, Map<MedioDePago, BigDecimal> cobradoPorMedio,
        Map<MedioDePago, BigDecimal> devueltoPorMedio) {
}
