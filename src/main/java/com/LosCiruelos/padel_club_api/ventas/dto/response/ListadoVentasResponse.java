package com.LosCiruelos.padel_club_api.ventas.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

/** Una página del listado de ventas del personal, con los totales de todo el filtro. */
public record ListadoVentasResponse(List<VentaResponse> contenido, int pagina, int tamanio, long totalElementos,
        int totalPaginas, Totales totales) {

    /**
     * @param ventas   cuántas ventas COBRADA hay en el filtro
     * @param anuladas cuántas ANULADA
     * @param total    lo cobrado neto: las cobradas menos lo devuelto
     * @param devuelto lo devuelto de esas ventas
     * @param porMedio lo cobrado neto de cada medio (todos los medios, con 0 si no hay)
     */
    public record Totales(long ventas, long anuladas, BigDecimal total, BigDecimal devuelto,
            Map<MedioDePago, BigDecimal> porMedio) {
    }
}
