package com.LosCiruelos.padel_club_api.reportes.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.reportes.repository.CobrosDelPeriodo;
import com.LosCiruelos.padel_club_api.reportes.repository.EntradasDeCaja;
import com.LosCiruelos.padel_club_api.reportes.repository.TurnosDelPeriodo;
import com.LosCiruelos.padel_club_api.reportes.dto.response.ResumenResponse;
import com.LosCiruelos.padel_club_api.reportes.dto.response.ResumenResponse.Caja;
import com.LosCiruelos.padel_club_api.reportes.dto.response.ResumenResponse.Cancelaciones;
import com.LosCiruelos.padel_club_api.reportes.dto.response.ResumenResponse.Jugados;
import com.LosCiruelos.padel_club_api.reportes.dto.response.ResumenResponse.PorJugar;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.reportes.repository.ResumenRepository;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Reloj;

import lombok.RequiredArgsConstructor;

/**
 * Resumen del período para el panel del club.
 * <ul>
 * <li>jugados, porJugar, cancelaciones y productos: reservas cuyo turno (fechaReserva) cae entre
 * desde y hasta, inclusive. Jugados son los turnos confirmados que ya terminaron; por jugar, los
 * que todavía no.</li>
 * <li>caja: pagos cobrados y devueltos en esas fechas, sea cual sea la fecha del turno (cierre de
 * caja).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ResumenService {

    public static final int DIAS_MAXIMOS = 366;

    // Pagos devueltos o por devolver: el club se queda con lo que no se devolvió
    private static final Set<EstadoPago> DEVUELTOS = Set.of(EstadoPago.DEVUELTO, EstadoPago.A_REEMBOLSAR,
            EstadoPago.REEMBOLSO_PENDIENTE);

    // Pagos que alguna vez se cobraron: entran a caja el día del cobro aunque después se devuelvan
    private static final Set<EstadoPago> COBRADOS = Set.of(EstadoPago.APROBADO, EstadoPago.DEVUELTO,
            EstadoPago.A_REEMBOLSAR, EstadoPago.REEMBOLSO_PENDIENTE);

    private final ResumenRepository resumenRepository;
    private final Reloj reloj;

    /**
     * @param desde       null junto con hasta: el día de hoy
     * @param soloHoy     true para el personal que no es ADMIN (EMPLEADO): solo puede ver el día de
     *                    hoy, no el historial
     */
    @Transactional(readOnly = true)
    public ResumenResponse resumen(LocalDate desde, LocalDate hasta, boolean soloHoy) {
        LocalDate hoy = reloj.hoy();
        if ((desde == null) != (hasta == null)) {
            throw new ReservaException("Indicá las dos fechas (desde y hasta) o ninguna para ver el día de hoy.");
        }
        if (desde == null) {
            desde = hoy;
            hasta = hoy;
        }
        if (soloHoy && !(desde.equals(hoy) && hasta.equals(hoy))) {
            throw new PermisoDenegadoException("Solo podés ver el resumen de hoy.");
        }
        return resumen(desde, hasta);
    }

    /** Resumen de cualquier período (ADMIN). */
    @Transactional(readOnly = true)
    public ResumenResponse resumen(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new ReservaException("La fecha desde no puede ser posterior a hasta.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > DIAS_MAXIMOS) {
            throw new ReservaException("El período no puede superar los " + DIAS_MAXIMOS + " días.");
        }

        // las que se juegan o se jugaron: señadas, parciales, pagas y completadas
        Set<EstadoReserva> juegan = Reserva.ESTADOS_QUE_SE_JUEGAN;
        LocalDateTime ahora = reloj.ahora();

        TurnosDelPeriodo t = resumenRepository.turnos(desde, hasta, ahora.toLocalDate(), ahora.toLocalTime(),
                juegan, EstadoReserva.CANCELADA);
        CobrosDelPeriodo c = resumenRepository.cobros(desde, hasta, ahora.toLocalDate(), ahora.toLocalTime(),
                juegan, EstadoReserva.CANCELADA, EstadoPago.APROBADO, DEVUELTOS, PaymentProvider.BONIFICACION);

        Jugados jugados = new Jugados(cantidad(t.jugados()), Dinero.redondear(t.montoJugados()), Dinero.redondear(c.cobradoJugados()),
                Dinero.redondear(c.bonificadoJugados()),
                Dinero.redondear(t.montoJugados().subtract(c.cobradoJugados()).subtract(c.bonificadoJugados())));
        PorJugar porJugar = new PorJugar(cantidad(t.porJugar()), Dinero.redondear(t.montoPorJugar()),
                Dinero.redondear(c.cobradoPorJugar()), Dinero.redondear(c.bonificadoPorJugar()),
                Dinero.redondear(t.montoPorJugar().subtract(c.cobradoPorJugar()).subtract(c.bonificadoPorJugar())));

        // caja: de las 00:00 de desde a las 00:00 del día siguiente a hasta
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();
        EntradasDeCaja e = resumenRepository.entradasDeCaja(inicio, fin, COBRADOS, PaymentProvider.MANUAL,
                PaymentProvider.MERCADO_PAGO, PaymentProvider.BONIFICACION);
        BigDecimal total = e.efectivo().add(e.mercadoPagoOnline()).add(e.mercadoPagoPresencial());
        BigDecimal devoluciones = resumenRepository.devoluciones(inicio, fin, EstadoPago.DEVUELTO,
                PaymentProvider.BONIFICACION);
        Caja caja = new Caja(Dinero.redondear(total), Dinero.redondear(e.efectivo()), Dinero.redondear(e.mercadoPagoOnline()),
                Dinero.redondear(e.mercadoPagoPresencial()), Dinero.redondear(devoluciones), Dinero.redondear(total.subtract(devoluciones)));

        return new ResumenResponse(desde, hasta, jugados, porJugar, caja,
                new Cancelaciones(cantidad(t.cancelados()), Dinero.redondear(c.retenidoCancelaciones())),
                List.copyOf(resumenRepository.productos(desde, hasta, juegan)));
    }

    /** SUM de un período sin reservas da null. */
    private static long cantidad(Long cantidad) {
        return cantidad == null ? 0 : cantidad;
    }

}
