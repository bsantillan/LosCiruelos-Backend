package com.LosCiruelos.padel_club_api.mapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.pagos.dto.response.DetalleMercadoPago;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.Caja;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.Cancelaciones;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.Jugados;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.PorJugar;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse.Ventas;
import com.LosCiruelos.padel_club_api.reservas.repository.CobrosDelPeriodo;
import com.LosCiruelos.padel_club_api.reservas.repository.EntradasDeCaja;
import com.LosCiruelos.padel_club_api.reservas.repository.ProductoVendido;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnosDelPeriodo;
import com.LosCiruelos.padel_club_api.ventas.repository.TotalesDeVentas;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaPorProducto;

/**
 * Las proyecciones del resumen → ResumenResponse: lo que falta cobrar (monto − cobrado −
 * bonificado), el total y el neto de caja con las ventas sueltas, los productos de las reservas
 * y de las ventas juntos, y los montos redondeados.
 */
@Component
public class ResumenMapper {

    public ResumenResponse aResponse(LocalDate desde, LocalDate hasta, java.time.LocalDateTime inicioCaja,
            java.time.LocalDateTime finCaja, TurnosDelPeriodo t, CobrosDelPeriodo c,
            EntradasDeCaja e, BigDecimal devolucionesDeReservas, List<ProductoVendido> productos, TotalesDeVentas v,
            BigDecimal devolucionesDeVentas, List<VentaPorProducto> vendidosSueltos) {
        Jugados jugados = new Jugados(cantidad(t.jugados()), Dinero.redondear(t.montoJugados()),
                Dinero.redondear(c.cobradoJugados()), Dinero.redondear(c.bonificadoJugados()),
                Dinero.redondear(t.montoJugados().subtract(c.cobradoJugados()).subtract(c.bonificadoJugados())));
        PorJugar porJugar = new PorJugar(cantidad(t.porJugar()), Dinero.redondear(t.montoPorJugar()),
                Dinero.redondear(c.cobradoPorJugar()), Dinero.redondear(c.bonificadoPorJugar()),
                Dinero.redondear(t.montoPorJugar().subtract(c.cobradoPorJugar()).subtract(c.bonificadoPorJugar())));

        BigDecimal efectivo = e.efectivo().add(v.efectivo());
        BigDecimal mercadoPago = e.mercadoPago().add(v.mercadoPago());
        DetalleMercadoPago detalle = new DetalleMercadoPago(Dinero.redondear(mercadoPago),
                Dinero.redondear(e.mercadoPagoQr().add(v.mercadoPagoQr())), Dinero.redondear(e.mercadoPagoOnline()),
                Dinero.redondear(e.transferencia().add(v.transferencia())));
        BigDecimal cuentaDni = e.cuentaDni().add(v.cuentaDni());
        BigDecimal total = efectivo.add(mercadoPago).add(cuentaDni);
        BigDecimal devoluciones = devolucionesDeReservas.add(devolucionesDeVentas);
        Caja caja = new Caja(Dinero.redondear(total), Dinero.redondear(efectivo), Dinero.redondear(mercadoPago),
                Dinero.redondear(cuentaDni),
                Dinero.redondear(devoluciones), Dinero.redondear(total.subtract(devoluciones)), detalle,
                Dinero.redondear(e.bonificado()), inicioCaja, finCaja);

        return new ResumenResponse(desde, hasta, jugados, porJugar, caja,
                new Cancelaciones(cantidad(t.cancelados()), Dinero.redondear(c.retenidoCancelaciones())),
                new Ventas(cantidad(v.cantidad()), Dinero.redondear(v.total()), Dinero.redondear(devolucionesDeVentas)),
                juntar(productos, vendidosSueltos));
    }

    /** Suma lo vendido suelto a lo pedido en las reservas, por producto y en orden de id. */
    private static List<ProductoVendido> juntar(List<ProductoVendido> deReservas, List<VentaPorProducto> sueltos) {
        Map<Long, ProductoVendido> porId = new TreeMap<>();
        deReservas.forEach(p -> porId.put(p.productoId(), p));
        for (VentaPorProducto s : sueltos) {
            porId.merge(s.productoId(), new ProductoVendido(s.productoId(), s.nombre(), s.tipo(), s.cantidad(), s.monto()),
                    (a, b) -> new ProductoVendido(a.productoId(), a.nombre(), a.tipo(), a.cantidad() + b.cantidad(),
                            a.monto().add(b.monto())));
        }
        return List.copyOf(porId.values());
    }

    /** SUM de un período sin reservas da null. */
    private static long cantidad(Long cantidad) {
        return cantidad == null ? 0 : cantidad;
    }
}
