package com.LosCiruelos.padel_club_api.reportes.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.reportes.repository.CobrosDelPeriodo;
import com.LosCiruelos.padel_club_api.reportes.repository.EntradasDeCaja;
import com.LosCiruelos.padel_club_api.reportes.repository.TurnosDelPeriodo;
import com.LosCiruelos.padel_club_api.reportes.dto.response.ResumenResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.ResumenMapper;
import com.LosCiruelos.padel_club_api.reportes.repository.ResumenRepository;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;

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

    private final ResumenRepository resumenRepository;
    private final ResumenMapper resumenMapper;
    private final UsuarioService usuarioService;
    private final Reloj reloj;

    /**
     * El ADMIN ve cualquier período; el EMPLEADO, solo el día de hoy (no el historial).
     *
     * @param desde null junto con hasta: el día de hoy
     */
    @Transactional(readOnly = true)
    public ResumenResponse resumen(LocalDate desde, LocalDate hasta, String email) {
        boolean soloHoy = !Roles.esAdmin(usuarioService.autenticado(email));
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
                juegan, EstadoReserva.CANCELADA, EstadoPago.APROBADO, Pago.ESTADOS_DEVUELTOS, PaymentProvider.BONIFICACION);

        // caja: de las 00:00 de desde a las 00:00 del día siguiente a hasta
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();
        EntradasDeCaja e = resumenRepository.entradasDeCaja(inicio, fin, Pago.ESTADOS_COBRADOS, PaymentProvider.MANUAL,
                PaymentProvider.MERCADO_PAGO, PaymentProvider.BONIFICACION);
        BigDecimal devoluciones = resumenRepository.devoluciones(inicio, fin, EstadoPago.DEVUELTO,
                PaymentProvider.BONIFICACION);
        return resumenMapper.aResponse(desde, hasta, t, c, e, devoluciones,
                resumenRepository.productos(desde, hasta, juegan));
    }
}
