package com.LosCiruelos.padel_club_api.Services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.DTOs.Resumen.CobrosPorEstado;
import com.LosCiruelos.padel_club_api.DTOs.Resumen.ReservasPorEstado;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ResumenResponse;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.PaymentProvider;
import com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.ResumenRepository;

import lombok.RequiredArgsConstructor;

/**
 * Resumen del período para el panel del club. La base son las reservas cuyo turno
 * (fechaReserva) cae entre desde y hasta, inclusive.
 */
@Service
@RequiredArgsConstructor
public class ResumenService {

    public static final int DIAS_MAXIMOS = 366;

    // Pagos devueltos o por devolver: el club se queda con lo que no se devolvió
    private static final Set<EstadoPago> DEVUELTOS = Set.of(EstadoPago.DEVUELTO, EstadoPago.A_REEMBOLSAR,
            EstadoPago.REEMBOLSO_PENDIENTE);

    private final ResumenRepository resumenRepository;
    private final Clock clock;

    /**
     * @param desde       null junto con hasta: el día de hoy
     * @param soloHoy     true para el personal que no es ADMIN (EMPLEADO): solo puede ver el día de
     *                    hoy, no el historial
     */
    @Transactional(readOnly = true)
    public ResumenResponse resumen(LocalDate desde, LocalDate hasta, boolean soloHoy) {
        LocalDate hoy = LocalDate.now(clock);
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

        // las que cuentan para lo esperado: señadas, parciales, pagas y completadas
        Set<EstadoReserva> esperadas = Reserva.ESTADOS_QUE_SE_JUEGAN;
        LocalDateTime ahora = LocalDateTime.now(clock);

        BigDecimal esperado = BigDecimal.ZERO;
        long jugados = 0;
        long cancelados = 0;
        for (ReservasPorEstado r : resumenRepository.reservasPorEstado(desde, hasta, ahora.toLocalDate(),
                ahora.toLocalTime())) {
            if (esperadas.contains(r.estado())) {
                esperado = esperado.add(r.montoTotal());
                jugados += r.terminadas();
            } else if (r.estado() == EstadoReserva.CANCELADA) {
                cancelados += r.cantidad();
            }
        }

        BigDecimal online = BigDecimal.ZERO;
        BigDecimal enClub = BigDecimal.ZERO;
        BigDecimal efectivo = BigDecimal.ZERO;
        BigDecimal bonificado = BigDecimal.ZERO;
        BigDecimal retenido = BigDecimal.ZERO;
        for (CobrosPorEstado c : resumenRepository.cobrosPorEstado(desde, hasta, EstadoPago.APROBADO, DEVUELTOS,
                PaymentProvider.MANUAL, PaymentProvider.BONIFICACION)) {
            if (esperadas.contains(c.estado())) {
                online = online.add(c.online());
                enClub = enClub.add(c.enClub());
                efectivo = efectivo.add(c.efectivo());
                bonificado = bonificado.add(c.bonificado());
            } else if (c.estado() == EstadoReserva.CANCELADA) {
                retenido = retenido.add(c.online()).add(c.enClub());
            }
        }
        BigDecimal cobrado = online.add(enClub);

        // por medio de pago: efectivo y Mercado Pago (online + tarjeta o QR en el mostrador)
        return new ResumenResponse(desde, hasta, pesos(esperado), pesos(cobrado), pesos(online), pesos(enClub),
                pesos(efectivo), pesos(cobrado.subtract(efectivo)), pesos(bonificado),
                pesos(esperado.subtract(cobrado).subtract(bonificado)), pesos(retenido), jugados, cancelados,
                List.copyOf(resumenRepository.productos(desde, hasta, esperadas)));
    }

    private static BigDecimal pesos(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP);
    }
}
