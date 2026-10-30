package com.LosCiruelos.padel_club_api.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.Caja;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.Cancelaciones;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.Jugados;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.PorJugar;
import com.LosCiruelos.padel_club_api.reservas.repository.CobrosDelPeriodo;
import com.LosCiruelos.padel_club_api.reservas.repository.EntradasDeCaja;
import com.LosCiruelos.padel_club_api.reservas.repository.ProductoVendido;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnosDelPeriodo;

/**
 * Las proyecciones del resumen → ResumenResponse: lo que falta cobrar (monto − cobrado −
 * bonificado), el total y el neto de caja, y los montos redondeados.
 */
@Component
public class ResumenMapper {

    public ResumenResponse aResponse(LocalDate desde, LocalDate hasta, TurnosDelPeriodo t, CobrosDelPeriodo c,
            EntradasDeCaja e, BigDecimal devoluciones, List<ProductoVendido> productos) {
        Jugados jugados = new Jugados(cantidad(t.jugados()), Dinero.redondear(t.montoJugados()),
                Dinero.redondear(c.cobradoJugados()), Dinero.redondear(c.bonificadoJugados()),
                Dinero.redondear(t.montoJugados().subtract(c.cobradoJugados()).subtract(c.bonificadoJugados())));
        PorJugar porJugar = new PorJugar(cantidad(t.porJugar()), Dinero.redondear(t.montoPorJugar()),
                Dinero.redondear(c.cobradoPorJugar()), Dinero.redondear(c.bonificadoPorJugar()),
                Dinero.redondear(t.montoPorJugar().subtract(c.cobradoPorJugar()).subtract(c.bonificadoPorJugar())));

        BigDecimal total = e.efectivo().add(e.mercadoPagoOnline()).add(e.mercadoPagoPresencial());
        Caja caja = new Caja(Dinero.redondear(total), Dinero.redondear(e.efectivo()),
                Dinero.redondear(e.mercadoPagoOnline()), Dinero.redondear(e.mercadoPagoPresencial()),
                Dinero.redondear(devoluciones), Dinero.redondear(total.subtract(devoluciones)));

        return new ResumenResponse(desde, hasta, jugados, porJugar, caja,
                new Cancelaciones(cantidad(t.cancelados()), Dinero.redondear(c.retenidoCancelaciones())),
                List.copyOf(productos));
    }

    /** SUM de un período sin reservas da null. */
    private static long cantidad(Long cantidad) {
        return cantidad == null ? 0 : cantidad;
    }
}
