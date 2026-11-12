package com.LosCiruelos.padel_club_api.reservas.exception;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

/**
 * 409: se quiere sacar o bajar una línea (o el precio de la cancha) por debajo de lo que ya tiene
 * cobrado. Primero hay que anular o devolver ese cobro. El body lleva {@code lineaId} (null para la
 * cancha) y {@code montoCobrado}.
 */
public class LineaCobradaException extends ReservaException {

    public LineaCobradaException(String nombre, Long lineaId, BigDecimal montoCobrado, boolean esCancha) {
        super(mensaje(nombre, montoCobrado, esCancha), HttpStatus.CONFLICT, datos(lineaId, montoCobrado));
    }

    private static String mensaje(String nombre, BigDecimal cobrado, boolean esCancha) {
        return esCancha
                ? "La cancha tiene %s cobrados: anulá o devolvé ese cobro antes de bajarle el precio."
                        .formatted(Dinero.texto(cobrado))
                : "%s tiene %s cobrados: anulá o devolvé ese cobro antes de sacarlo."
                        .formatted(nombre, Dinero.texto(cobrado));
    }

    private static Map<String, Object> datos(Long lineaId, BigDecimal cobrado) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("lineaId", lineaId); // null: la cancha
        datos.put("montoCobrado", Dinero.redondear(cobrado));
        return datos;
    }
}
