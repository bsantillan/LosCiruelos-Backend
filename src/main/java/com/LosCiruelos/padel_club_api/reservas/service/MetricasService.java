package com.LosCiruelos.padel_club_api.reservas.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.club.service.CanchaService;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.club.service.JornadaService;
import com.LosCiruelos.padel_club_api.club.service.ProductoService;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.reservas.dto.response.MetricasResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.repository.PrimeraReserva;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.reservas.service.CalculoDeMetricas.CanchaDeMetricas;
import com.LosCiruelos.padel_club_api.reservas.service.CalculoDeMetricas.Periodo;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.ventas.service.VentaConsultaService;

import lombok.RequiredArgsConstructor;

/**
 * Las métricas del club para el ADMIN (GET /admin/metricas): junta los datos del período pedido y del
 * anterior de igual duración y los pasa a {@link CalculoDeMetricas}.
 */
@Service
@RequiredArgsConstructor
public class MetricasService {

    public static final int DIAS_MAXIMOS = 366;

    private final ReservaRepository reservaRepository;
    private final VentaConsultaService ventaConsultaService;
    private final ProductoService productoService;
    private final CanchaService canchaService;
    private final ConfiguracionService configuracionService;
    private final JornadaService jornadaService;
    private final UsuarioService usuarioService;
    private final Reloj reloj;

    /** @param desde null junto con hasta: los últimos 30 días hasta hoy */
    @Transactional(readOnly = true)
    public MetricasResponse metricas(LocalDate desde, LocalDate hasta, String email) {
        Roles.exigirAdmin(usuarioService.autenticado(email), "Solo el ADMIN ve las métricas.");
        LocalDateTime ahora = reloj.ahora();
        LocalDate hoy = ahora.toLocalDate();
        if ((desde == null) != (hasta == null)) {
            throw new ReservaException("Indicá las dos fechas (desde y hasta) o ninguna para ver los últimos 30 días.");
        }
        if (desde == null) {
            desde = hoy.minusDays(29);
            hasta = hoy;
        }
        if (desde.isAfter(hasta)) {
            throw new ReservaException("La fecha desde no puede ser posterior a hasta.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > DIAS_MAXIMOS) {
            throw new ReservaException("El período no puede superar los " + DIAS_MAXIMOS + " días.");
        }
        Periodo actual = new Periodo(desde, hasta);
        Periodo anterior = actual.anterior();

        List<CanchaDeMetricas> canchas = canchaService.listarCanchas().stream()
                .map(c -> new CanchaDeMetricas(c.getId(), c.getNumero())).toList();
        CalculoDeMetricas calculo = new CalculoDeMetricas(configuracionService.obtener(), canchas.size(), ahora);

        // las reservas de los dos períodos (por fecha del turno), en cualquier estado
        List<Reserva> deAmbos = reservaRepository.findByFechaReservaBetween(anterior.desde(), actual.hasta());
        List<Reserva> delPeriodo = deAmbos.stream().filter(r -> actual.incluye(r.getFechaReserva())).toList();
        List<Reserva> delAnterior = deAmbos.stream().filter(r -> anterior.incluye(r.getFechaReserva())).toList();
        List<Reserva> confirmadas = confirmadas(delPeriodo);
        List<Reserva> confirmadasAntes = confirmadas(delAnterior);

        // las creadas en cada período (por fecha de creación)
        List<Reserva> creadasDeAmbos = reservaRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                anterior.desde().atStartOfDay(), actual.hasta().plusDays(1).atStartOfDay());
        List<Reserva> creadas = creadasDeAmbos.stream().filter(r -> actual.incluye(r.getCreatedAt())).toList();
        List<Reserva> creadasAntes = creadasDeAmbos.stream().filter(r -> anterior.incluye(r.getCreatedAt())).toList();

        // lo que viene: confirmadas y pendientes de pago desde hoy
        List<Reserva> futuras = reservaRepository.findByFechaReservaGreaterThanEqualAndEstadoIn(hoy,
                java.util.EnumSet.of(EstadoReserva.PENDIENTE, EstadoReserva.CONFIRMADA, EstadoReserva.SEÑADA,
                        EstadoReserva.PARCIALMENTE_PAGADA, EstadoReserva.PAGADA));
        List<Reserva> proximas = futuras.stream()
                .filter(r -> r.getEstado() != EstadoReserva.PENDIENTE || r.estaPendienteVigente(ahora)).toList();
        List<Reserva> futurasConfirmadas = futuras.stream()
                .filter(r -> Reserva.ESTADOS_CONFIRMADOS.contains(r.getEstado()) && !r.turnoEmpezo(ahora)).toList();

        Map<Long, LocalDate> primeras = reservaRepository.primerasReservas(Reserva.ESTADOS_QUE_SE_JUEGAN).stream()
                .collect(Collectors.toMap(PrimeraReserva::clienteId, PrimeraReserva::fecha));
        List<Reserva> ultimos120 = confirmadas(reservaRepository.findByFechaReservaBetween(hoy.minusDays(120), hoy));

        return new MetricasResponse(desde, hasta, new MetricasResponse.Periodo(anterior.desde(), anterior.hasta()),
                calculo.ocupacion(actual, confirmadas, confirmadasAntes, proximas, canchas),
                calculo.ingresos(confirmadas, confirmadasAntes, ventasNetas(actual), ventasNetas(anterior)),
                calculo.clientes(actual, confirmadas, confirmadasAntes, primeras, ultimos120),
                calculo.reservas(creadas, creadasAntes, confirmadas),
                calculo.cancelaciones(delPeriodo, delAnterior),
                calculo.deuda(reservaRepository.findTerminadasConSaldo(Reserva.ESTADOS_CON_SALDO, hoy,
                        ahora.toLocalTime())),
                calculo.futuro(futurasConfirmadas),
                calculo.productos(reservaRepository.resumenProductos(desde, hasta, Reserva.ESTADOS_QUE_SE_JUEGAN),
                        ventaConsultaService.porProducto(jornadaService.del(desde).inicio(), jornadaService.del(hasta).fin()),
                        confirmadas, productoService.activos()));
    }

    private static List<Reserva> confirmadas(List<Reserva> reservas) {
        return reservas.stream().filter(r -> Reserva.ESTADOS_QUE_SE_JUEGAN.contains(r.getEstado())).toList();
    }

    /** Lo vendido suelto en las jornadas del período, menos lo devuelto. */
    private BigDecimal ventasNetas(Periodo periodo) {
        LocalDateTime inicio = jornadaService.del(periodo.desde()).inicio();
        LocalDateTime fin = jornadaService.del(periodo.hasta()).fin();
        return ventaConsultaService.totales(inicio, fin).total().subtract(ventaConsultaService.devoluciones(inicio, fin));
    }
}
