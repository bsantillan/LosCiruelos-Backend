package com.LosCiruelos.padel_club_api.DTOs.Responses;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.LosCiruelos.padel_club_api.DTOs.Resumen.ProductoVendido;

/** Resumen del período para el panel del club (reservas cuyo turno cae entre desde y hasta). */
public record ResumenResponse(
        LocalDate desde,
        LocalDate hasta,
        BigDecimal montoEsperado,
        BigDecimal montoCobrado,
        BigDecimal cobradoOnline,
        BigDecimal cobradoEnClub,
        BigDecimal cobradoEfectivo,
        BigDecimal cobradoMercadoPago,
        BigDecimal montoBonificado,
        BigDecimal montoPendiente,
        BigDecimal retenidoCancelaciones,
        long partidosJugados,
        long partidosCancelados,
        List<ProductoVendido> productos) {
}
