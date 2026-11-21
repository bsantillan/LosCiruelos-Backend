package com.LosCiruelos.padel_club_api.reservas.dto.response;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Página de reservas para el panel del personal: el mismo formato que PaginaResponse más los
 * totales del filtro completo (no solo de la página).
 */
public record ListadoReservasResponse(List<ReservaResponse> contenido, int pagina, int tamanio, long totalElementos,
        int totalPaginas, Totales totales, com.LosCiruelos.padel_club_api.common.RangoDeFechas rango) {

    /**
     * @param montoCobrado       lo que el club se quedó de los pagos (aprobado menos lo devuelto)
     * @param cobradoEfectivo    de eso, lo cobrado en efectivo
     * @param cobradoMercadoPago de eso, lo cobrado con Mercado Pago (online o en el club)
     * @param cobradoCuentaDni   de eso, lo cobrado con Cuenta DNI
     * @param montoBonificado    lo que el ADMIN bonificó (no es plata ni cuenta como cobrado)
     * @param montoPendiente     lo que deben las reservas SEÑADA y PARCIALMENTE_PAGADA
     */
    /** @param cobradoTarjeta con tarjeta en otro posnet (la del posnet de Mercado Pago va en cobradoMercadoPago) */
    public record Totales(long reservas, BigDecimal montoTotal, BigDecimal montoCobrado, BigDecimal cobradoEfectivo,
            BigDecimal cobradoMercadoPago, BigDecimal cobradoCuentaDni, BigDecimal cobradoTarjeta, BigDecimal montoBonificado,
            BigDecimal montoPendiente, com.LosCiruelos.padel_club_api.pagos.dto.response.DetalleMercadoPago
                    detalleMercadoPago) {
    }

    public static ListadoReservasResponse de(Page<ReservaResponse> page, Totales totales,
            com.LosCiruelos.padel_club_api.common.RangoDeFechas rango) {
        return new ListadoReservasResponse(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), totales, rango);
    }
}
