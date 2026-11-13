package com.LosCiruelos.padel_club_api.reservas.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.entity.DiaApertura;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Fechas;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.CanalDePago;
import com.LosCiruelos.padel_club_api.reservas.dto.response.MetricasResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.MetricasResponse.Comparado;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaProducto;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.TipoReserva;
import com.LosCiruelos.padel_club_api.reservas.repository.ProductoVendido;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaPorProducto;

/**
 * Las cuentas de las métricas del ADMIN, sin Spring: recibe las reservas, el horario del club y las
 * canchas, y arma cada bloque. La horas se miden en el horario semanal del club (sin los días
 * cerrados) por la cantidad de canchas.
 */
public final class CalculoDeMetricas {

    /** Un producto de venta con estas unidades o menos tiene stock bajo. */
    public static final int STOCK_BAJO = 5;
    static final int TOP = 10;

    private final Configuracion config;
    private final int canchas;
    private final LocalDateTime ahora;

    public CalculoDeMetricas(Configuracion config, int canchas, LocalDateTime ahora) {
        this.config = config;
        this.canchas = canchas;
        this.ahora = ahora;
    }

    /** Un período de fechas de turno, inclusive. */
    public record Periodo(LocalDate desde, LocalDate hasta) {

        boolean incluye(LocalDate fecha) {
            return !fecha.isBefore(desde) && !fecha.isAfter(hasta);
        }

        boolean incluye(LocalDateTime momento) {
            return incluye(momento.toLocalDate());
        }

        /** El período anterior de igual duración. */
        public Periodo anterior() {
            long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
            return new Periodo(desde.minusDays(dias), desde.minusDays(1));
        }

        List<LocalDate> fechas() {
            return desde.datesUntil(hasta.plusDays(1)).toList();
        }
    }

    // ── Utilidades ───────────────────────────────────────────────────────

    static Comparado comparar(BigDecimal actual, BigDecimal anterior) {
        BigDecimal variacion = anterior.signum() == 0 ? null
                : actual.subtract(anterior).multiply(BigDecimal.valueOf(100)).divide(anterior.abs(), 1, RoundingMode.HALF_UP);
        return new Comparado(actual, anterior, variacion);
    }

    static Comparado comparar(long actual, long anterior) {
        return comparar(BigDecimal.valueOf(actual), BigDecimal.valueOf(anterior));
    }

    private static BigDecimal porcentaje(BigDecimal parte, BigDecimal total) {
        return total.signum() == 0 ? BigDecimal.ZERO.setScale(1)
                : parte.multiply(BigDecimal.valueOf(100)).divide(total, 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal horas(long minutos) {
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal dividir(BigDecimal a, BigDecimal b) {
        return b.signum() == 0 ? Dinero.redondear(BigDecimal.ZERO) : a.divide(b, 2, RoundingMode.HALF_UP);
    }

    private static long minutosEntre(LocalTime desde, LocalTime hasta) {
        return Math.max(0, Duration.between(desde, hasta).toMinutes());
    }

    /** Minutos en que se superponen [a1, a2) y [b1, b2). */
    private static long superposicion(LocalTime a1, LocalTime a2, LocalTime b1, LocalTime b2) {
        LocalTime desde = a1.isAfter(b1) ? a1 : b1;
        LocalTime hasta = a2.isBefore(b2) ? a2 : b2;
        return minutosEntre(desde, hasta);
    }

    /** El fin de la franja de 1 hora que empieza a las {@code hora} (la de las 23 termina a las 23:59:59). */
    private static LocalTime finDeHora(int hora) {
        return hora == 23 ? LocalTime.MAX : LocalTime.of(hora + 1, 0);
    }

    static String nombre(Usuario usuario) {
        String nombre = usuario.getNombre() != null ? usuario.getNombre() : "";
        String apellido = usuario.getApellido() != null ? usuario.getApellido() : "";
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? usuario.getEmail() : completo;
    }

    private long minutosAbiertos(LocalDate fecha) {
        return config.horarioDel(fecha)
                .map(h -> minutosEntre(h.getHorario_inicio(), h.getHorario_fin()) * canchas)
                .orElse(0L);
    }

    private static long minutos(Reserva r) {
        return r.turno().duracionMinutos();
    }

    // ── Ocupación ────────────────────────────────────────────────────────

    public MetricasResponse.Ocupacion ocupacion(Periodo actual, List<Reserva> confirmadas,
            List<Reserva> confirmadasAnterior, List<Reserva> proximas, List<CanchaDeMetricas> listaCanchas) {
        long abiertas = actual.fechas().stream().mapToLong(this::minutosAbiertos).sum();
        long abiertasAntes = actual.anterior().fechas().stream().mapToLong(this::minutosAbiertos).sum();
        long ocupadas = confirmadas.stream().mapToLong(CalculoDeMetricas::minutos).sum();
        long ocupadasAntes = confirmadasAnterior.stream().mapToLong(CalculoDeMetricas::minutos).sum();

        long abiertasPorCancha = canchas == 0 ? 0 : abiertas / canchas;
        List<MetricasResponse.OcupacionCancha> porCancha = listaCanchas.stream().map(c -> {
            List<Reserva> deLaCancha = confirmadas.stream().filter(r -> r.getCancha().getId().equals(c.id())).toList();
            long minutos = deLaCancha.stream().mapToLong(CalculoDeMetricas::minutos).sum();
            return new MetricasResponse.OcupacionCancha(c.id(), c.numero(),
                    porcentaje(BigDecimal.valueOf(minutos), BigDecimal.valueOf(abiertasPorCancha)), horas(minutos),
                    Dinero.redondear(Dinero.sumar(deLaCancha.stream().map(Reserva::getMontoTotal))));
        }).toList();

        return new MetricasResponse.Ocupacion(
                comparar(porcentaje(BigDecimal.valueOf(ocupadas), BigDecimal.valueOf(abiertas)),
                        porcentaje(BigDecimal.valueOf(ocupadasAntes), BigDecimal.valueOf(abiertasAntes))),
                comparar(horas(ocupadas), horas(ocupadasAntes)), comparar(horas(abiertas), horas(abiertasAntes)),
                porCancha, mapa(actual, confirmadas), porFranja(actual, confirmadas), proximos7Dias(proximas));
    }

    /** Día de la semana × hora: lo ocupado sobre lo abierto en esa franja de 1 hora. */
    List<MetricasResponse.DiaDelMapa> mapa(Periodo periodo, List<Reserva> confirmadas) {
        Map<DayOfWeek, long[]> abierto = new EnumMap<>(DayOfWeek.class);
        Map<DayOfWeek, long[]> ocupado = new EnumMap<>(DayOfWeek.class);
        for (DayOfWeek d : DayOfWeek.values()) {
            abierto.put(d, new long[24]);
            ocupado.put(d, new long[24]);
        }
        int primera = 24;
        int ultima = -1;
        for (LocalDate fecha : periodo.fechas()) {
            Optional<DiaApertura> horario = config.horarioDel(fecha);
            if (horario.isEmpty()) {
                continue;
            }
            for (int h = 0; h < 24; h++) {
                long minutos = superposicion(horario.get().getHorario_inicio(), horario.get().getHorario_fin(),
                        LocalTime.of(h, 0), finDeHora(h));
                if (minutos > 0) {
                    abierto.get(fecha.getDayOfWeek())[h] += minutos * canchas;
                    primera = Math.min(primera, h);
                    ultima = Math.max(ultima, h);
                }
            }
        }
        for (Reserva r : confirmadas) {
            for (int h = 0; h < 24; h++) {
                ocupado.get(r.getFechaReserva().getDayOfWeek())[h] += superposicion(r.getHoraInicio(), r.getHoraFin(),
                        LocalTime.of(h, 0), finDeHora(h));
            }
        }
        List<MetricasResponse.DiaDelMapa> mapa = new ArrayList<>();
        for (DayOfWeek d : DayOfWeek.values()) {
            List<MetricasResponse.FranjaDelMapa> franjas = new ArrayList<>();
            for (int h = primera; h <= ultima; h++) {
                long a = abierto.get(d)[h];
                franjas.add(new MetricasResponse.FranjaDelMapa(Fechas.textoHora(LocalTime.of(h, 0)),
                        a == 0 ? null : porcentaje(BigDecimal.valueOf(ocupado.get(d)[h]), BigDecimal.valueOf(a))));
            }
            mapa.add(new MetricasResponse.DiaDelMapa(d, franjas));
        }
        return mapa;
    }

    private static final Map<String, LocalTime[]> FRANJAS = new LinkedHashMap<>();
    static {
        FRANJAS.put("MANIANA", new LocalTime[] { LocalTime.MIDNIGHT, LocalTime.of(12, 0) });
        FRANJAS.put("TARDE", new LocalTime[] { LocalTime.of(12, 0), LocalTime.of(18, 0) });
        FRANJAS.put("NOCHE", new LocalTime[] { LocalTime.of(18, 0), LocalTime.MAX });
    }

    /** Mañana, tarde y noche: % ocupado e ingreso de cancha por hora ocupada (para decidir precios por franja). */
    List<MetricasResponse.OcupacionFranja> porFranja(Periodo periodo, List<Reserva> confirmadas) {
        List<MetricasResponse.OcupacionFranja> franjas = new ArrayList<>();
        FRANJAS.forEach((nombre, limites) -> {
            long abierto = 0;
            for (LocalDate fecha : periodo.fechas()) {
                abierto += config.horarioDel(fecha).map(h -> superposicion(h.getHorario_inicio(), h.getHorario_fin(),
                        limites[0], limites[1]) * canchas).orElse(0L);
            }
            long ocupado = 0;
            BigDecimal ingreso = BigDecimal.ZERO;
            for (Reserva r : confirmadas) {
                long minutos = superposicion(r.getHoraInicio(), r.getHoraFin(), limites[0], limites[1]);
                if (minutos > 0) {
                    ocupado += minutos;
                    ingreso = ingreso.add(r.getMontoCancha().multiply(BigDecimal.valueOf(minutos))
                            .divide(BigDecimal.valueOf(minutos(r)), 2, RoundingMode.HALF_UP));
                }
            }
            franjas.add(new MetricasResponse.OcupacionFranja(nombre,
                    porcentaje(BigDecimal.valueOf(ocupado), BigDecimal.valueOf(abierto)), horas(ocupado),
                    dividir(ingreso, horas(ocupado))));
        });
        return franjas;
    }

    /** Desde hoy, 7 días: lo ocupado por reservas confirmadas o pendientes de pago. */
    List<MetricasResponse.OcupacionDia> proximos7Dias(List<Reserva> proximas) {
        LocalDate hoy = ahora.toLocalDate();
        List<MetricasResponse.OcupacionDia> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate fecha = hoy.plusDays(i);
            long abierto = minutosAbiertos(fecha);
            long ocupado = proximas.stream().filter(r -> r.getFechaReserva().equals(fecha))
                    .mapToLong(CalculoDeMetricas::minutos).sum();
            dias.add(new MetricasResponse.OcupacionDia(fecha,
                    porcentaje(BigDecimal.valueOf(ocupado), BigDecimal.valueOf(abierto)), horas(ocupado), horas(abierto)));
        }
        return dias;
    }

    /** Lo que las métricas necesitan de una cancha. */
    public record CanchaDeMetricas(Long id, Integer numero) {
    }

    // ── Ingresos ─────────────────────────────────────────────────────────

    /** Lo facturado de un período (las reservas confirmadas y las ventas sueltas netas). */
    record Facturado(BigDecimal cancha, BigDecimal productos, BigDecimal ventas, BigDecimal online,
            BigDecimal enElClub, long reservas, long minutos) {

        BigDecimal total() {
            return cancha.add(productos).add(ventas);
        }

        BigDecimal ticket() {
            return reservas == 0 ? Dinero.redondear(BigDecimal.ZERO)
                    : cancha.add(productos).divide(BigDecimal.valueOf(reservas), 2, RoundingMode.HALF_UP);
        }

        BigDecimal porHora() {
            return dividir(cancha, horas(minutos));
        }
    }

    static Facturado facturado(List<Reserva> confirmadas, BigDecimal ventasNetas) {
        BigDecimal online = BigDecimal.ZERO;
        BigDecimal enElClub = BigDecimal.ZERO;
        for (Reserva r : confirmadas) {
            for (Pago p : r.getPagos()) {
                if (p.esBonificacion()) {
                    continue;
                }
                if (p.canal() == CanalDePago.MERCADO_PAGO_ONLINE) {
                    online = online.add(p.getMontoRetenido());
                } else {
                    enElClub = enElClub.add(p.getMontoRetenido());
                }
            }
        }
        return new Facturado(Dinero.sumar(confirmadas.stream().map(Reserva::getMontoCancha)),
                Dinero.sumar(confirmadas.stream().map(Reserva::getMontoProductos)), ventasNetas, online, enElClub,
                confirmadas.size(), confirmadas.stream().mapToLong(CalculoDeMetricas::minutos).sum());
    }

    public MetricasResponse.Ingresos ingresos(List<Reserva> confirmadas, List<Reserva> confirmadasAnterior,
            BigDecimal ventasNetas, BigDecimal ventasNetasAnterior) {
        Facturado a = facturado(confirmadas, ventasNetas);
        Facturado b = facturado(confirmadasAnterior, ventasNetasAnterior);
        return new MetricasResponse.Ingresos(comparar(r(a.total()), r(b.total())), comparar(r(a.cancha()), r(b.cancha())),
                comparar(r(a.productos()), r(b.productos())), comparar(r(a.ventas()), r(b.ventas())),
                comparar(r(a.online()), r(b.online())), comparar(r(a.enElClub()), r(b.enElClub())),
                comparar(a.ticket(), b.ticket()), comparar(a.porHora(), b.porHora()));
    }

    private static BigDecimal r(BigDecimal monto) {
        return Dinero.redondear(monto);
    }

    // ── Clientes ─────────────────────────────────────────────────────────

    public MetricasResponse.Clientes clientes(Periodo actual, List<Reserva> confirmadas,
            List<Reserva> confirmadasAnterior, Map<Long, LocalDate> primeraReserva, List<Reserva> ultimos120Dias) {
        Map<Long, List<Reserva>> porCliente = confirmadas.stream()
                .collect(Collectors.groupingBy(r -> r.getCliente().getId(), LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<Reserva>> porClienteAntes = confirmadasAnterior.stream()
                .collect(Collectors.groupingBy(r -> r.getCliente().getId()));
        long nuevos = porCliente.keySet().stream().filter(id -> esNuevo(id, actual, primeraReserva)).count();
        long nuevosAntes = porClienteAntes.keySet().stream()
                .filter(id -> esNuevo(id, actual.anterior(), primeraReserva)).count();

        List<MetricasResponse.ClienteDestacado> destacados = porCliente.values().stream()
                .map(rs -> new MetricasResponse.ClienteDestacado(rs.getFirst().getCliente().getId(),
                        nombre(rs.getFirst().getCliente()), rs.size(),
                        r(Dinero.sumar(rs.stream().map(Reserva::getMontoTotal)))))
                .toList();

        return new MetricasResponse.Clientes(comparar(porCliente.size(), porClienteAntes.size()),
                comparar(nuevos, nuevosAntes),
                comparar(porCliente.size() - nuevos, porClienteAntes.size() - nuevosAntes),
                comparar(dividir(BigDecimal.valueOf(confirmadas.size()), BigDecimal.valueOf(porCliente.size())),
                        dividir(BigDecimal.valueOf(confirmadasAnterior.size()), BigDecimal.valueOf(porClienteAntes.size()))),
                destacados.stream().sorted(Comparator.comparingLong(MetricasResponse.ClienteDestacado::reservas).reversed()
                        .thenComparing(MetricasResponse.ClienteDestacado::gasto, Comparator.reverseOrder())).limit(TOP)
                        .toList(),
                destacados.stream().sorted(Comparator.comparing(MetricasResponse.ClienteDestacado::gasto).reversed())
                        .limit(TOP).toList(),
                dejaronDeVenir(ultimos120Dias));
    }

    private static boolean esNuevo(Long clienteId, Periodo periodo, Map<Long, LocalDate> primeraReserva) {
        LocalDate primera = primeraReserva.get(clienteId);
        return primera != null && periodo.incluye(primera);
    }

    /** 3 o más reservas confirmadas entre hace 120 y hace 30 días, y ninguna en los últimos 30. */
    List<MetricasResponse.ClienteQueDejoDeVenir> dejaronDeVenir(List<Reserva> ultimos120Dias) {
        LocalDate hoy = ahora.toLocalDate();
        LocalDate corte = hoy.minusDays(30);
        Map<Long, List<Reserva>> porCliente = ultimos120Dias.stream()
                .collect(Collectors.groupingBy(r -> r.getCliente().getId()));
        List<MetricasResponse.ClienteQueDejoDeVenir> lista = new ArrayList<>();
        porCliente.forEach((id, rs) -> {
            boolean vinoHace30 = rs.stream().anyMatch(r -> !r.getFechaReserva().isBefore(corte));
            List<Reserva> antes = rs.stream().filter(r -> r.getFechaReserva().isBefore(corte)).toList();
            if (!vinoHace30 && antes.size() >= 3) {
                Usuario cliente = rs.getFirst().getCliente();
                lista.add(new MetricasResponse.ClienteQueDejoDeVenir(id, nombre(cliente), cliente.getTelefono(),
                        antes.size(), antes.stream().map(Reserva::getFechaReserva).max(Comparator.naturalOrder()).orElseThrow()));
            }
        });
        lista.sort(Comparator.comparingLong(MetricasResponse.ClienteQueDejoDeVenir::reservasAntes).reversed());
        return lista;
    }

    // ── Reservas ─────────────────────────────────────────────────────────

    /** Las creadas a mano (sin los partidos que genera solo un turno fijo). */
    static List<Reserva> creadasAMano(List<Reserva> creadas) {
        return creadas.stream().filter(r -> r.getTurnoFijo() == null).toList();
    }

    private static BigDecimal anticipacionPromedio(List<Reserva> creadas) {
        if (creadas.isEmpty()) {
            return BigDecimal.ZERO.setScale(1);
        }
        long minutos = creadas.stream()
                .mapToLong(r -> Math.max(0, Duration.between(r.getCreatedAt(), r.inicioDelTurno()).toMinutes())).sum();
        return BigDecimal.valueOf(minutos).divide(BigDecimal.valueOf(60L * creadas.size()), 1, RoundingMode.HALF_UP);
    }

    public MetricasResponse.Reservas reservas(List<Reserva> creadas, List<Reserva> creadasAnterior,
            List<Reserva> confirmadas) {
        List<Reserva> aMano = creadasAMano(creadas);
        List<Reserva> aManoAntes = creadasAMano(creadasAnterior);
        long mismoDia = 0;
        long unoADos = 0;
        long tresASiete = 0;
        long mas = 0;
        for (Reserva r : aMano) {
            long dias = ChronoUnit.DAYS.between(r.getCreatedAt().toLocalDate(), r.getFechaReserva());
            if (dias <= 0) {
                mismoDia++;
            } else if (dias <= 2) {
                unoADos++;
            } else if (dias <= 7) {
                tresASiete++;
            } else {
                mas++;
            }
        }
        Map<Long, Long> duraciones = confirmadas.stream()
                .collect(Collectors.groupingBy(CalculoDeMetricas::minutos, TreeMap::new, Collectors.counting()));
        long turnosFijos = confirmadas.stream().filter(r -> r.getTurnoFijo() != null).count();
        long partidos = confirmadas.stream()
                .filter(r -> r.getTurnoFijo() == null && r.getTipo() == TipoReserva.PARTIDO).count();
        long porCliente = aMano.stream().filter(r -> r.getCreatedBy().getRol() == Role.CLIENTE).count();
        return new MetricasResponse.Reservas(comparar(aMano.size(), aManoAntes.size()),
                comparar(anticipacionPromedio(aMano), anticipacionPromedio(aManoAntes)),
                new MetricasResponse.Anticipacion(mismoDia, unoADos, tresASiete, mas),
                duraciones.entrySet().stream()
                        .map(e -> new MetricasResponse.Duracion(e.getKey(), e.getValue()))
                        .sorted(Comparator.comparingLong(MetricasResponse.Duracion::cantidad).reversed())
                        .toList(),
                new MetricasResponse.PorTipo(confirmadas.size() - turnosFijos - partidos, partidos, turnosFijos),
                new MetricasResponse.PorOrigen(porCliente, aMano.size() - porCliente));
    }

    // ── Cancelaciones ────────────────────────────────────────────────────

    private record Cancelaciones(long canceladas, long confirmadas, long vencidas, BigDecimal retenido) {

        BigDecimal tasa() {
            return porcentaje(BigDecimal.valueOf(canceladas), BigDecimal.valueOf(canceladas + confirmadas));
        }
    }

    private static Cancelaciones contar(List<Reserva> delPeriodo) {
        List<Reserva> canceladas = delPeriodo.stream().filter(r -> r.getEstado() == EstadoReserva.CANCELADA).toList();
        return new Cancelaciones(canceladas.size(),
                delPeriodo.stream().filter(r -> Reserva.ESTADOS_QUE_SE_JUEGAN.contains(r.getEstado())).count(),
                delPeriodo.stream().filter(r -> r.getEstado() == EstadoReserva.EXPIRADA).count(),
                Dinero.sumar(canceladas.stream().flatMap(r -> r.getPagos().stream())
                        .filter(p -> !p.esBonificacion()).map(Pago::getMontoRetenido)));
    }

    /** @param delPeriodo todas las reservas del período (por fecha del turno), en cualquier estado */
    public MetricasResponse.Cancelaciones cancelaciones(List<Reserva> delPeriodo, List<Reserva> delPeriodoAnterior) {
        Cancelaciones a = contar(delPeriodo);
        Cancelaciones b = contar(delPeriodoAnterior);
        long porCliente = 0;
        long porClub = 0;
        long sinDato = 0;
        long[] anticipacion = new long[4];
        for (Reserva r : delPeriodo) {
            if (r.getEstado() != EstadoReserva.CANCELADA) {
                continue;
            }
            if (r.getCanceladaAt() == null) {
                sinDato++;
                continue;
            }
            if (r.getCanceladaPor() != null && r.getCanceladaPor().getRol() == Role.CLIENTE) {
                porCliente++;
            } else {
                porClub++;
            }
            long horas = Duration.between(r.getCanceladaAt(), r.inicioDelTurno()).toMinutes();
            anticipacion[horas < 120 ? 0 : horas < 24 * 60 ? 1 : horas < 72 * 60 ? 2 : 3]++;
        }
        return new MetricasResponse.Cancelaciones(comparar(a.tasa(), b.tasa()), comparar(a.canceladas(), b.canceladas()),
                porCliente, porClub, sinDato,
                new MetricasResponse.AnticipacionCancelacion(anticipacion[0], anticipacion[1], anticipacion[2],
                        anticipacion[3]),
                comparar(r(a.retenido()), r(b.retenido())), comparar(a.vencidas(), b.vencidas()));
    }

    // ── Deuda y futuro ───────────────────────────────────────────────────

    /** @param conSaldo turnos ya jugados que deben saldo */
    public MetricasResponse.Deuda deuda(List<Reserva> conSaldo) {
        LocalDate hoy = ahora.toLocalDate();
        BigDecimal hasta7 = BigDecimal.ZERO;
        BigDecimal hasta30 = BigDecimal.ZERO;
        BigDecimal mas = BigDecimal.ZERO;
        for (Reserva r : conSaldo) {
            long dias = ChronoUnit.DAYS.between(r.getFechaReserva(), hoy);
            if (dias <= 7) {
                hasta7 = hasta7.add(r.getMontoPendiente());
            } else if (dias <= 30) {
                hasta30 = hasta30.add(r.getMontoPendiente());
            } else {
                mas = mas.add(r.getMontoPendiente());
            }
        }
        Map<Long, List<Reserva>> porCliente = conSaldo.stream()
                .collect(Collectors.groupingBy(r -> r.getCliente().getId()));
        List<MetricasResponse.Deudor> deudores = porCliente.values().stream().map(rs -> {
            Usuario cliente = rs.getFirst().getCliente();
            return new MetricasResponse.Deudor(cliente.getId(), nombre(cliente), cliente.getTelefono(),
                    r(Dinero.sumar(rs.stream().map(Reserva::getMontoPendiente))), rs.size(),
                    rs.stream().map(Reserva::getFechaReserva).min(Comparator.naturalOrder()).orElseThrow());
        }).sorted(Comparator.comparing(MetricasResponse.Deudor::monto).reversed()).toList();
        return new MetricasResponse.Deuda(r(hasta7.add(hasta30).add(mas)), r(hasta7), r(hasta30), r(mas), deudores);
    }

    /** @param futuras los turnos confirmados que todavía no empezaron */
    public MetricasResponse.Futuro futuro(List<Reserva> futuras) {
        LocalDate tope = ahora.toLocalDate().plusDays(13);
        List<Reserva> proximas14 = futuras.stream().filter(rr -> !rr.getFechaReserva().isAfter(tope)).toList();
        return new MetricasResponse.Futuro(r(Dinero.sumar(futuras.stream().map(Reserva::getMontoPagado))),
                r(Dinero.sumar(proximas14.stream().map(Reserva::getMontoPendiente))), proximas14.size());
    }

    // ── Productos ────────────────────────────────────────────────────────

    public MetricasResponse.Productos productos(List<ProductoVendido> deReservas, List<VentaPorProducto> sueltos,
            List<Reserva> confirmadas, List<Producto> catalogo) {
        Map<Long, MetricasResponse.ProductoMovido> vendidos = new HashMap<>();
        Function<MetricasResponse.ProductoMovido, Long> id = MetricasResponse.ProductoMovido::productoId;
        deReservas.stream().filter(p -> p.tipo() == TipoProducto.VENTA).forEach(p -> sumar(vendidos,
                new MetricasResponse.ProductoMovido(p.productoId(), p.nombre(), p.tipo(), p.cantidad(), r(p.monto()))));
        sueltos.stream().filter(p -> p.tipo() == TipoProducto.VENTA).forEach(p -> sumar(vendidos,
                new MetricasResponse.ProductoMovido(p.productoId(), p.nombre(), p.tipo(), p.cantidad(), r(p.monto()))));
        List<MetricasResponse.ProductoMovido> masVendidos = vendidos.values().stream()
                .sorted(Comparator.comparingLong(MetricasResponse.ProductoMovido::cantidad).reversed().thenComparing(id))
                .limit(TOP).toList();
        List<MetricasResponse.ProductoMovido> alquilados = deReservas.stream()
                .filter(p -> p.tipo() == TipoProducto.ALQUILER)
                .map(p -> new MetricasResponse.ProductoMovido(p.productoId(), p.nombre(), p.tipo(), p.cantidad(),
                        r(p.monto())))
                .sorted(Comparator.comparingLong(MetricasResponse.ProductoMovido::cantidad).reversed())
                .toList();

        long unidades = 0;
        BigDecimal monto = BigDecimal.ZERO;
        long reservas = 0;
        long sinRegistrar = 0;
        for (Reserva rr : confirmadas) {
            boolean faltan = false;
            boolean pendiente = false;
            for (ReservaProducto linea : rr.getProductos()) {
                if (!linea.esAlquiler()) {
                    continue;
                }
                if (linea.devolucionRegistrada() && linea.getCantidadDevuelta() < linea.getCantidad()) {
                    int faltantes = linea.getCantidad() - linea.getCantidadDevuelta();
                    unidades += faltantes;
                    monto = monto.add(linea.getPrecioUnitario().multiply(BigDecimal.valueOf(faltantes)));
                    faltan = true;
                } else if (!linea.devolucionRegistrada() && rr.turnoTermino(ahora)) {
                    pendiente = true;
                }
            }
            reservas += faltan ? 1 : 0;
            sinRegistrar += pendiente ? 1 : 0;
        }
        List<MetricasResponse.ProductoConPocoStock> stockBajo = catalogo.stream()
                .filter(p -> Boolean.TRUE.equals(p.getActivo()) && p.getTipo() == TipoProducto.VENTA
                        && p.getStock() != null && p.getStock() <= STOCK_BAJO)
                .sorted(Comparator.comparing(Producto::getStock))
                .map(p -> new MetricasResponse.ProductoConPocoStock(p.getId(), p.getNombre(), p.getStock()))
                .toList();
        return new MetricasResponse.Productos(masVendidos, alquilados,
                new MetricasResponse.NoDevueltos(unidades, r(monto), reservas, sinRegistrar), stockBajo);
    }

    private static void sumar(Map<Long, MetricasResponse.ProductoMovido> vendidos, MetricasResponse.ProductoMovido p) {
        vendidos.merge(p.productoId(), p, (a, b) -> new MetricasResponse.ProductoMovido(a.productoId(), a.nombre(),
                a.tipo(), a.cantidad() + b.cantidad(), r(a.monto().add(b.monto()))));
    }
}
