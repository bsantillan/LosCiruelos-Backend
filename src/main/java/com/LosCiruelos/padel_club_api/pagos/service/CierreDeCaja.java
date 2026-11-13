package com.LosCiruelos.padel_club_api.pagos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CajaResponse;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CajaResponse.Movimiento;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.MedioDeCaja;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.TipoMovimientoCaja;

/**
 * Las cuentas del cierre de caja, sin Spring: con los movimientos de la jornada arma lo cobrado y lo
 * devuelto por medio. Lo cobrado es la suma de los cobros y ventas menos sus anulaciones (así queda
 * sin lo anulado); lo devuelto, la suma de las devoluciones.
 */
final class CierreDeCaja {

    private CierreDeCaja() {
    }

    static CajaResponse armar(LocalDate fecha, LocalDateTime inicio, LocalDateTime fin, List<Movimiento> movimientos,
            BigDecimal bonificado, long cantidadAEntregar, BigDecimal montoAEntregar) {
        List<Movimiento> ordenados = movimientos.stream()
                .sorted(Comparator.comparing(Movimiento::fecha).thenComparing(m -> m.tipo().ordinal()))
                .toList();
        CajaResponse.PorMedio cobrado = porMedio(ordenados, m -> m.tipo() != TipoMovimientoCaja.DEVOLUCION, false);
        CajaResponse.PorMedio devuelto = porMedio(ordenados, m -> m.tipo() == TipoMovimientoCaja.DEVOLUCION, true);
        return new CajaResponse(fecha, inicio, fin, cobrado, devuelto,
                Dinero.redondear(cobrado.efectivo().subtract(devuelto.efectivo())), Dinero.redondear(bonificado),
                new CajaResponse.AEntregar(cantidadAEntregar, Dinero.redondear(montoAEntregar)), ordenados);
    }

    private static CajaResponse.PorMedio porMedio(List<Movimiento> movimientos, Predicate<Movimiento> cuales,
            boolean enPositivo) {
        BigDecimal efectivo = suma(movimientos, cuales, MedioDeCaja.EFECTIVO, enPositivo);
        BigDecimal mercadoPago = suma(movimientos, cuales, MedioDeCaja.MERCADO_PAGO, enPositivo);
        BigDecimal cuentaDni = suma(movimientos, cuales, MedioDeCaja.CUENTA_DNI, enPositivo);
        return new CajaResponse.PorMedio(efectivo, mercadoPago, cuentaDni,
                Dinero.redondear(efectivo.add(mercadoPago).add(cuentaDni)));
    }

    private static BigDecimal suma(List<Movimiento> movimientos, Predicate<Movimiento> cuales, MedioDeCaja medio,
            boolean enPositivo) {
        BigDecimal suma = Dinero.sumar(movimientos.stream().filter(cuales).filter(m -> m.medio() == medio)
                .map(Movimiento::monto));
        return Dinero.redondear(enPositivo ? suma.negate() : suma);
    }
}
