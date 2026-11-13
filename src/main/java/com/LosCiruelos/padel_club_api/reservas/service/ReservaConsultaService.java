package com.LosCiruelos.padel_club_api.reservas.service;

import com.LosCiruelos.padel_club_api.club.service.JornadaService;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnosDelPeriodo;
import com.LosCiruelos.padel_club_api.reservas.repository.EntradasDeCaja;
import com.LosCiruelos.padel_club_api.reservas.repository.CobrosDelPeriodo;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ResumenResponse;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.mapper.ResumenMapper;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.common.PaginaResponse;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.reservas.exception.ReservaNoEncontradaException;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.common.Busquedas;
import com.LosCiruelos.padel_club_api.common.Paginacion;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.mapper.ReservaMapper;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ListadoReservasResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.TipoReserva;
import com.LosCiruelos.padel_club_api.reservas.repository.FiltroReservas;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaSpecification;

import com.LosCiruelos.padel_club_api.ventas.service.VentaConsultaService;
import lombok.RequiredArgsConstructor;

/**
 * Consultas de reservas (solo lectura): "Mis reservas", el detalle, el listado del personal con
 * sus totales, el resumen del período, los turnos jugados que deben saldo, las paletas sin
 * devolver y los números del perfil del jugador.
 */
@Service
@RequiredArgsConstructor
public class ReservaConsultaService {

    public static final int TAMANIO_MAXIMO_MIS_RESERVAS = 50;
    public static final int TAMANIO_POR_DEFECTO_LISTADO = 20;
    public static final int TAMANIO_MAXIMO_LISTADO = 100;
    public static final int DIAS_MAXIMOS_RESUMEN = 366;

    private final UsuarioService usuarioService;
    private final ReservaRepository reservaRepository;
    private final ReservaMapper mapper;
    private final ResumenMapper resumenMapper;
    private final VentaConsultaService ventaConsultaService;
    private final Reloj reloj;
    private final JornadaService jornadaService;

    // ── Del cliente ──────────────────────────────────────────────────────

    /**
     * Reservas del usuario, filtradas y paginadas.
     *
     * @param tipo "proximas" (turno sin terminar que ocupa la cancha), "historial" (el resto) o null (todas)
     * @param incluirDescartadas si es false (por defecto) no se muestran las canceladas y vencidas sin
     *                           ningún pago que haya movido dinero, aunque se filtre por esos estados
     * Orden fijo: próximas de la más cercana a la más lejana; historial y todas, de la más nueva a la más vieja.
     */
    @Transactional(readOnly = true)
    public PaginaResponse<ReservaResponse> misReservas(String email, String tipo, List<EstadoReserva> estados,
            LocalDate desde, LocalDate hasta, boolean incluirDescartadas, int pagina, int tamanio) {
        Paginacion.validar(pagina, tamanio, TAMANIO_MAXIMO_MIS_RESERVAS);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReservaException("La fecha desde no puede ser posterior a hasta.");
        }

        Usuario usuario = usuarioService.autenticado(email);
        LocalDateTime ahora = reloj.ahora();

        // las suyas y los partidos en los que juega
        Specification<Reserva> filtro = ReservaSpecification.delClienteOJugador(usuario);
        Sort orden = Sort.by(Sort.Direction.DESC, "fechaReserva", "horaInicio");
        if (tipo != null && !tipo.isBlank()) {
            switch (tipo.trim().toLowerCase()) {
                case "proximas" -> {
                    filtro = filtro.and(ReservaSpecification.proximas(ahora));
                    orden = Sort.by(Sort.Direction.ASC, "fechaReserva", "horaInicio");
                }
                case "historial" -> filtro = filtro.and(ReservaSpecification.historial(ahora));
                default -> throw new ReservaException("El tipo tiene que ser 'proximas' o 'historial'.");
            }
        }
        if (estados != null && !estados.isEmpty()) {
            filtro = filtro.and(ReservaSpecification.conEstados(estados));
        }
        // las descartadas se ocultan siempre (también filtrando por estado); como es parte del
        // filtro, el conteo del paginado (totalElementos) también las excluye
        if (!incluirDescartadas) {
            filtro = filtro.and(ReservaSpecification.sinDescartadas());
        }
        if (desde != null) {
            filtro = filtro.and(ReservaSpecification.desde(desde));
        }
        if (hasta != null) {
            filtro = filtro.and(ReservaSpecification.hasta(hasta));
        }

        Page<ReservaResponse> resultado = reservaRepository.findAll(filtro, PageRequest.of(pagina, tamanio, orden))
                .map(r -> mapper.aResponsePara(r, usuario));
        return PaginaResponse.de(resultado);
    }

    /** Detalle de una reserva: la propia, la de un partido en el que juega, o cualquiera para el personal. */
    @Transactional(readOnly = true)
    public ReservaResponse obtener(Long id, String email) {
        Usuario usuario = usuarioService.autenticado(email);
        Reserva reserva = reservaRepository.findById(id).orElseThrow(() -> new ReservaNoEncontradaException(id));
        // al EMPLEADO, una de un día anterior no le existe (como las ventas de otros días)
        if (!reserva.puedeVerla(usuario) || reserva.quedaFueraDelEmpleado(usuario, jornadaService.actual().fecha())) {
            throw new ReservaNoEncontradaException(id);
        }
        return mapper.aResponsePara(reserva, usuario);
    }

    // ── Del personal ─────────────────────────────────────────────────────

    /**
     * Listado del panel del personal: pasadas y futuras, de cualquier cliente, con filtros
     * combinables y los totales del filtro completo.
     *
     * @param tipo       NORMAL o PARTIDO (null: las dos)
     * @param pendientes solo las que deben plata: CONFIRMADA, SEÑADA y PARCIALMENTE_PAGADA (hayan jugado o no).
     *                   Se combina con {@code estados}: quedan las que cumplen los dos
     * @param orden      "asc" o "desc" por fecha y hora del turno; null: "asc" si desde es hoy o
     *                   posterior (próximas) y "desc" en el resto (historial)
     * @param email      quien consulta: el EMPLEADO solo ve de hoy en adelante (sin desde, desde hoy;
     *                   con fechas anteriores, 403); los totales respetan ese límite
     */
    @Transactional(readOnly = true)
    public ListadoReservasResponse listar(LocalDate desde, LocalDate hasta, List<EstadoReserva> estados,
            Long clienteId, String cliente, Long canchaId, TipoReserva tipo, Long turnoFijoId, Boolean deTurnoFijo,
            boolean pendientes, int pagina, Integer tamanio, String orden, String email) {
        if (Roles.esEmpleado(usuarioService.autenticado(email))) {
            LocalDate hoy = jornadaService.actual().fecha();
            if ((desde != null && desde.isBefore(hoy)) || (hasta != null && hasta.isBefore(hoy))) {
                throw new PermisoDenegadoException("Solo podés ver las reservas de hoy en adelante.");
            }
            if (desde == null) {
                desde = hoy;
            }
        }
        int size = tamanio != null ? tamanio : TAMANIO_POR_DEFECTO_LISTADO;
        Paginacion.validar(pagina, size, TAMANIO_MAXIMO_LISTADO);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReservaException("La fecha desde no puede ser posterior a hasta.");
        }
        if (turnoFijoId != null && Boolean.FALSE.equals(deTurnoFijo)) {
            throw new ReservaException("No se puede pedir un turno fijo (turnoFijoId) y las que no son de turnos fijos "
                    + "(deTurnoFijo=false) a la vez.");
        }

        FiltroReservas filtro = new FiltroReservas(desde, hasta, estadosEfectivos(estados, pendientes),
                clienteId, Busquedas.normalizar(cliente), canchaId, tipo, turnoFijoId, deTurnoFijo);
        Sort.Direction direccion = direccion(orden, desde);
        PageRequest pageable = PageRequest.of(pagina, size,
                Sort.by(direccion, "fechaReserva", "horaInicio").and(Sort.by(direccion, "id")));

        Page<ReservaResponse> resultado = reservaRepository.findAll(ReservaSpecification.paraPersonal(filtro), pageable)
                .map(mapper::aResponse);
        return ListadoReservasResponse.de(resultado, reservaRepository.totalesParaPersonal(filtro));
    }

    /**
     * Turnos ya jugados que deben saldo (con el turno terminado), del más viejo al más nuevo. Al
     * registrar el pago del saldo pasan a COMPLETADA. Al EMPLEADO, solo los de hoy.
     */
    @Transactional(readOnly = true)
    public List<ReservaResponse> saldosPendientes(String email) {
        Usuario personal = usuarioService.autenticado(email);
        LocalDateTime ahora = reloj.ahora();
        LocalDate jornada = jornadaService.actual().fecha();
        return reservaRepository.findTerminadasConSaldo(Reserva.ESTADOS_CON_SALDO, ahora.toLocalDate(),
                ahora.toLocalTime()).stream()
                .filter(r -> !r.quedaFueraDelEmpleado(personal, jornada))
                .map(mapper::aResponse)
                .toList();
    }

    /** Reservas cuyo turno ya empezó y todavía tienen paletas sin devolver. Al EMPLEADO, solo las de hoy. */
    @Transactional(readOnly = true)
    public List<ReservaResponse> devolucionesPendientes(String email) {
        Usuario personal = usuarioService.autenticado(email);
        LocalDateTime ahora = reloj.ahora();
        LocalDate jornada = jornadaService.actual().fecha();
        return reservaRepository.findConAlquileresSinDevolver(TipoProducto.ALQUILER, Reserva.ESTADOS_QUE_SE_JUEGAN,
                ahora.toLocalDate(), ahora.toLocalTime()).stream()
                .filter(r -> !r.quedaFueraDelEmpleado(personal, jornada))
                .map(mapper::aResponse)
                .toList();
    }

    // ── Resumen del período (panel del club) ─────────────────────────────
    // jugados, porJugar, cancelaciones y productos: reservas cuyo turno (fechaReserva) cae entre
    // desde y hasta, inclusive (jugados: confirmados que ya terminaron; por jugar: los que no).
    // caja: pagos cobrados y devueltos en esas fechas, sea cual sea el turno (cierre de caja).

    /**
     * El ADMIN ve cualquier período; el EMPLEADO, solo el día de hoy (no el historial).
     *
     * @param desde null junto con hasta: el día de hoy
     */
    @Transactional(readOnly = true)
    public ResumenResponse resumen(LocalDate desde, LocalDate hasta, String email) {
        // solo el ADMIN: el EMPLEADO ve el cierre de su jornada en GET /admin/caja
        Roles.exigirAdmin(usuarioService.autenticado(email), "Solo el ADMIN ve el resumen.");
        LocalDate hoy = jornadaService.actual().fecha();
        if ((desde == null) != (hasta == null)) {
            throw new ReservaException("Indicá las dos fechas (desde y hasta) o ninguna para ver el día de hoy.");
        }
        if (desde == null) {
            desde = hoy;
            hasta = hoy;
        }
        return resumen(desde, hasta);
    }

    /** Resumen de cualquier período (ADMIN). */
    @Transactional(readOnly = true)
    public ResumenResponse resumen(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new ReservaException("La fecha desde no puede ser posterior a hasta.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) + 1 > DIAS_MAXIMOS_RESUMEN) {
            throw new ReservaException("El período no puede superar los " + DIAS_MAXIMOS_RESUMEN + " días.");
        }

        // las que se juegan o se jugaron: señadas, parciales, pagas y completadas
        Set<EstadoReserva> juegan = Reserva.ESTADOS_QUE_SE_JUEGAN;
        LocalDateTime ahora = reloj.ahora();

        TurnosDelPeriodo t = reservaRepository.resumenTurnos(desde, hasta, ahora.toLocalDate(), ahora.toLocalTime(),
                juegan, EstadoReserva.CANCELADA);
        CobrosDelPeriodo c = reservaRepository.resumenCobros(desde, hasta, ahora.toLocalDate(), ahora.toLocalTime(),
                juegan, EstadoReserva.CANCELADA, EstadoPago.APROBADO, Pago.ESTADOS_DEVUELTOS, PaymentProvider.BONIFICACION);

        // caja: por jornada (lo que pasó entre la apertura de desde y la apertura del día siguiente a
        // hasta), igual para el ADMIN y el EMPLEADO; un cobro de las 00:10 es de la jornada anterior
        LocalDateTime inicio = jornadaService.del(desde).inicio();
        LocalDateTime fin = jornadaService.del(hasta).fin();
        EntradasDeCaja e = reservaRepository.resumenEntradasDeCaja(inicio, fin, Pago.ESTADOS_COBRADOS, PaymentProvider.MANUAL,
                PaymentProvider.MERCADO_PAGO, PaymentProvider.TRANSFERENCIA_MP, PaymentProvider.CUENTA_DNI, PaymentProvider.BONIFICACION);
        BigDecimal devoluciones = reservaRepository.resumenDevoluciones(inicio, fin,
                com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoDevolucion.DEVUELTO,
                PaymentProvider.BONIFICACION);
        return resumenMapper.aResponse(desde, hasta, inicio, fin, t, c, e, devoluciones,
                reservaRepository.resumenProductos(desde, hasta, juegan), ventaConsultaService.totales(inicio, fin),
                ventaConsultaService.devoluciones(inicio, fin), ventaConsultaService.porProducto(inicio, fin));
    }

    // ── Números del perfil del jugador ───────────────────────────────────

    public long contarPorEstado(Usuario cliente, EstadoReserva estado) {
        return reservaRepository.countByClienteAndEstado(cliente, estado);
    }

    public Long countPartidosEsteMes(Long clienteId, EstadoReserva estado) {
        LocalDate hoy = reloj.hoy();
        return reservaRepository.countPartidosEsteMes(
                clienteId, estado, hoy.withDayOfMonth(1), hoy.withDayOfMonth(hoy.lengthOfMonth()));
    }

    public Optional<Reserva> ultimaReservaCompletada(Usuario cliente) {
        return reservaRepository.findFirstByClienteAndEstadoOrderByFechaReservaDescHoraInicioDesc(
                cliente, EstadoReserva.COMPLETADA);
    }

    // ── Auxiliares del listado ───────────────────────────────────────────

    /** null: todos los estados. Con "pendientes", solo las que deben plata (de las pedidas). */
    static Set<EstadoReserva> estadosEfectivos(List<EstadoReserva> estados, boolean pendientes) {
        Set<EstadoReserva> pedidos = estados == null || estados.isEmpty() ? null : EnumSet.copyOf(estados);
        if (!pendientes) {
            return pedidos;
        }
        Set<EstadoReserva> queDeben = EnumSet.copyOf(Reserva.ESTADOS_CON_SALDO);
        if (pedidos != null) {
            queDeben.retainAll(pedidos);
        }
        return queDeben;
    }

    private Sort.Direction direccion(String orden, LocalDate desde) {
        if (orden == null || orden.isBlank()) {
            boolean proximas = desde != null && !desde.isBefore(reloj.hoy());
            return proximas ? Sort.Direction.ASC : Sort.Direction.DESC;
        }
        return switch (orden.trim().toLowerCase()) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new ReservaException("El orden tiene que ser 'asc' o 'desc'.");
        };
    }
}
