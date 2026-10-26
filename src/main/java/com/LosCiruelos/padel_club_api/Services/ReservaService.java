package com.LosCiruelos.padel_club_api.Services;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.DTOs.Requests.CrearReservaRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PaginaResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ReservaResponse;
import com.LosCiruelos.padel_club_api.Entities.Cancha;
import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.Entities.DiaApertura;
import com.LosCiruelos.padel_club_api.Entities.Pago;
import com.LosCiruelos.padel_club_api.Entities.Producto;
import com.LosCiruelos.padel_club_api.Entities.ReservaProducto;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.TurnoFijo;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Entities.Enum.TipoProducto;
import com.LosCiruelos.padel_club_api.Exceptions.CanchaNotFoundException;
import com.LosCiruelos.padel_club_api.Exceptions.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.Exceptions.DiaSinAperturaException;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.HorarioOcupadoException;
import com.LosCiruelos.padel_club_api.Exceptions.PerfilIncompletoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaNoEncontradaException;
import com.LosCiruelos.padel_club_api.Repository.CanchaRepository;
import com.LosCiruelos.padel_club_api.Repository.PagoRepository;
import com.LosCiruelos.padel_club_api.Repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.Repository.TurnoFijoRepository;
import com.LosCiruelos.padel_club_api.Specifications.ReservaSpecification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reservas de canchas.
 *
 * Regla de concurrencia: toda operación que modifica una reserva o sus pagos la
 * carga primero con {@link #bloquear(Long)} (SELECT ... FOR UPDATE) y después
 * trabaja con {@code reserva.getPagos()}. La creación además bloquea la cancha.
 * El stock de productos se mueve al final, en un solo paso, después de bloquear
 * todas las reservas involucradas (ver {@link ProductoService#moverStock}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaService {

    public static final int MINUTOS_PARA_PAGAR = 15;

    static final Set<EstadoReserva> ESTADOS_INACTIVOS = Set.of(EstadoReserva.CANCELADA, EstadoReserva.EXPIRADA);
    static final Set<EstadoPago> PAGOS_ACTIVOS = Set.of(EstadoPago.APROBADO, EstadoPago.EN_PROCESO);

    private final ConfiguracionService configuracionService;
    private final UsuarioService usuarioService;
    private final ClienteProfileService clienteProfileService;
    private final ReservaRepository reservaRepository;
    private final CanchaRepository canchaRepository;
    private final PagoRepository pagoRepository;
    private final ProductoService productoService;
    private final PoliticaCancelacion politicaCancelacion;
    private final TurnoFijoRepository turnoFijoRepository;
    private final Clock clock;

    // ── Consultas ────────────────────────────────────────────────────────

    public static final int TAMANIO_MAXIMO_PAGINA = 50;

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
        if (pagina < 0) {
            throw new ReservaException("La página no puede ser negativa.");
        }
        if (tamanio < 1 || tamanio > TAMANIO_MAXIMO_PAGINA) {
            throw new ReservaException("El tamaño de página tiene que estar entre 1 y " + TAMANIO_MAXIMO_PAGINA + ".");
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReservaException("La fecha desde no puede ser posterior a hasta.");
        }

        Usuario usuario = usuarioService.findByEmailOrThrow(email,
                new CredencialesInvalidasException("Usuario no encontrado"));
        LocalDateTime ahora = LocalDateTime.now(clock);

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
                .map(r -> aResponsePara(r, usuario));
        return PaginaResponse.de(resultado);
    }

    @Transactional(readOnly = true)
    public ReservaResponse obtener(Long id, String email) {
        Usuario usuario = usuarioService.findByEmailOrThrow(email,
                new CredencialesInvalidasException("Usuario no encontrado"));
        Reserva reserva = reservaRepository.findById(id).orElseThrow(() -> new ReservaNoEncontradaException(id));
        verificarLectura(reserva, usuario);
        return aResponsePara(reserva, usuario);
    }

    public Reserva save(Reserva reserva) {
        return reservaRepository.save(reserva);
    }

    public long contarPorEstado(Usuario cliente, EstadoReserva estado) {
        return reservaRepository.countByClienteAndEstado(cliente, estado);
    }

    public Long countPartidosEsteMes(Long clienteId, EstadoReserva estado) {
        LocalDate hoy = LocalDate.now(clock);
        return reservaRepository.countPartidosEsteMes(
                clienteId, estado, hoy.withDayOfMonth(1), hoy.withDayOfMonth(hoy.lengthOfMonth()));
    }

    public Optional<Reserva> ultimaReservaCompletada(Usuario cliente) {
        return reservaRepository.findFirstByClienteAndEstadoOrderByFechaReservaDescHoraInicioDesc(
                cliente, EstadoReserva.COMPLETADA);
    }

    // ── Bloqueo y acceso ─────────────────────────────────────────────────

    /** Carga la reserva con lock de escritura. Debe ser lo primero que se lee de ella en la transacción. */
    public Reserva bloquear(Long id) {
        return reservaRepository.findByIdForUpdate(id).orElseThrow(() -> new ReservaNoEncontradaException(id));
    }

    public Reserva bloquearConAcceso(Long id, Usuario usuario) {
        Reserva reserva = bloquear(id);
        verificarAcceso(reserva, usuario);
        return reserva;
    }

    /**
     * Un CLIENTE solo puede operar sus propias reservas; ADMIN y EMPLEADO, cualquiera.
     * Si no tiene acceso se responde "no encontrada" para no revelar ids ajenos.
     */
    public void verificarAcceso(Reserva reserva, Usuario usuario) {
        boolean esDuenio = reserva.getCliente().getId().equals(usuario.getId());
        if (!esPersonal(usuario) && !esDuenio) {
            throw new ReservaNoEncontradaException(reserva.getId());
        }
    }

    /** Para ver la reserva alcanza con jugar el partido (pero no para pagarlo ni cancelarlo). */
    void verificarLectura(Reserva reserva, Usuario usuario) {
        if (!reserva.esPartido() || !reserva.esJugador(usuario)) {
            verificarAcceso(reserva, usuario);
        }
    }

    static boolean esPersonal(Usuario usuario) {
        return usuario.getRol() == Role.ADMIN || usuario.getRol() == Role.EMPLEADO;
    }

    // ── Creación ─────────────────────────────────────────────────────────

    @Transactional
    public ReservaResponse crearReserva(CrearReservaRequest req, String emailSolicitante) {
        LocalDateTime ahora = LocalDateTime.now(clock);

        Usuario solicitante = usuarioService.findByEmailOrThrow(emailSolicitante,
                new CredencialesInvalidasException("Usuario no encontrado"));
        Usuario cliente = resolverCliente(req.getClienteId(), solicitante);
        validarPerfilCompleto(cliente, solicitante);

        Configuracion config = configuracionService.obtener();
        long duracion = validarHorario(config, req.getFechaReserva(), req.getHoraInicio(), req.getHoraFin(), ahora);

        // Lock de la cancha: los pedidos simultáneos para esta cancha esperan su turno
        Cancha cancha = canchaRepository.findByIdForUpdate(req.getCanchaId())
                .orElseThrow(() -> new CanchaNotFoundException("Cancha no encontrada: " + req.getCanchaId()));

        // Reservas que dejan de estar activas en esta operación: su stock se devuelve al final
        List<Reserva> aLiberar = new ArrayList<>();

        // 1. Las pendientes vencidas que se superponen se expiran ahora (el job corre cada minuto)
        aLiberar.addAll(expirarVencidasQueSeSuperponen(cancha.getId(), req.getFechaReserva(), req.getHoraInicio(),
                req.getHoraFin(), ahora));

        // 2. Mismo cliente, cancha, fecha e inicio con una PENDIENTE vigente: cambió la duración o los productos
        Optional<Long> existente = reservaRepository.findPendienteVigenteMismoInicio(cliente.getId(),
                cancha.getId(), req.getFechaReserva(), req.getHoraInicio(), ahora, EstadoReserva.PENDIENTE);
        if (existente.isPresent()) {
            return aResponse(actualizarPendiente(bloquear(existente.get()), req, duracion, config, ahora, aLiberar));
        }

        // 3. Otras PENDIENTE del cliente en esa cancha y fecha sin pagos activos: eligió otro horario
        for (Long id : reservaRepository.findPendientesSinPagoActivo(cliente.getId(), cancha.getId(),
                req.getFechaReserva(), ahora, EstadoReserva.PENDIENTE, PAGOS_ACTIVOS)) {
            Reserva anterior = bloquear(id);
            if (anterior.estaPendienteVigente(ahora) && !anterior.tienePagoActivo()) {
                marcarCancelada(anterior);
                aLiberar.add(anterior);
                log.info("Reserva #{} cancelada automáticamente: el cliente eligió otro horario", id);
            }
        }

        // 4. Solapamiento con otras reservas y con los turnos fijos
        validarLibre(cancha.getId(), req.getFechaReserva(), req.getHoraInicio(), req.getHoraFin(), ahora, null);

        Reserva reserva = Reserva.builder()
                .cancha(cancha)
                .cliente(cliente)
                .createdBy(solicitante)
                .fechaReserva(req.getFechaReserva())
                .horaInicio(req.getHoraInicio())
                .horaFin(req.getHoraFin())
                .estado(EstadoReserva.PENDIENTE)
                .createdAt(ahora)
                .expiresAt(ahora.plusMinutes(MINUTOS_PARA_PAGAR))
                .build();

        // 5. Stock: devuelve lo de las reservas que se expiraron o cancelaron y toma lo pedido
        productoService.moverStock(aLiberar, reserva, pedidoDeProductos(req), ahora);
        reserva.setMontoTotal(configuracionService.calcularMontoTurno(config, duracion).add(reserva.getMontoProductos()));

        reserva = guardarVerificandoSolapamiento(reserva);
        log.info("Reserva #{} creada para el cliente {} en la cancha {} el {} de {} a {}",
                reserva.getId(), cliente.getId(), cancha.getNumero(), reserva.getFechaReserva(),
                reserva.getHoraInicio(), reserva.getHoraFin());
        return aResponse(reserva);
    }

    private Reserva actualizarPendiente(Reserva reserva, CrearReservaRequest req, long duracion,
            Configuracion config, LocalDateTime ahora, List<Reserva> aLiberar) {
        if (reserva.tienePagoActivo()) {
            throw new EstadoInvalidoException("La reserva ya tiene un pago en curso o aprobado; no se puede modificar.");
        }

        Map<String, Integer> pedido = pedidoDeProductos(req);
        boolean cambiaFin = !reserva.getHoraFin().equals(req.getHoraFin());
        boolean cambianProductos = pedido.entrySet().stream()
                .anyMatch(e -> reserva.cantidadDe(e.getKey()) != e.getValue());

        if (!cambiaFin && !cambianProductos) {
            productoService.moverStock(aLiberar, null, Map.of(), ahora);
            return reserva;
        }

        if (cambiaFin) {
            validarLibre(reserva.getCancha().getId(), reserva.getFechaReserva(), req.getHoraInicio(),
                    req.getHoraFin(), ahora, reserva.getId());
            reserva.setHoraFin(req.getHoraFin());
        }

        // devuelve el stock de los productos anteriores y toma los nuevos (los alquileres se
        // vuelven a validar porque pudo cambiar el horario)
        productoService.moverStock(aLiberar, reserva, pedido, ahora);

        BigDecimal montoAnterior = reserva.getMontoTotal();
        reserva.setMontoTotal(configuracionService.calcularMontoTurno(config, duracion).add(reserva.getMontoProductos()));
        if (reserva.getMontoTotal().compareTo(montoAnterior) != 0) {
            // un pago iniciado con el monto anterior ya no sirve
            expirarPagosPendientes(reserva);
        }
        log.info("Reserva #{} actualizada: {} a {}, {} pelotas, {} paletas", reserva.getId(),
                reserva.getHoraInicio(), reserva.getHoraFin(),
                reserva.cantidadDe(Producto.CODIGO_PELOTAS), reserva.cantidadDe(Producto.CODIGO_PALETAS));
        return guardarVerificandoSolapamiento(reserva);
    }

    /** Cantidades pedidas por código de producto (null = 0). */
    private static Map<String, Integer> pedidoDeProductos(CrearReservaRequest req) {
        return pedidoDeProductos(req.getCantidadPelotas(), req.getCantidadPaletas());
    }

    static Map<String, Integer> pedidoDeProductos(Integer pelotas, Integer paletas) {
        return Map.of(
                Producto.CODIGO_PELOTAS, pelotas != null ? pelotas : 0,
                Producto.CODIGO_PALETAS, paletas != null ? paletas : 0);
    }

    /**
     * Expira (con lock) las PENDIENTE vencidas sin pagos activos que se superponen con el turno:
     * todavía cuentan para la restricción de la base. Devuelve las expiradas, para liberar su stock.
     */
    List<Reserva> expirarVencidasQueSeSuperponen(Long canchaId, LocalDate fecha, LocalTime inicio, LocalTime fin,
            LocalDateTime ahora) {
        List<Reserva> expiradas = new ArrayList<>();
        for (Long id : reservaRepository.findPendientesVencidasQueSeSuperponen(canchaId, fecha, inicio, fin, ahora,
                EstadoReserva.PENDIENTE, PAGOS_ACTIVOS)) {
            Reserva vencida = bloquear(id);
            if (vencida.estaVencida(ahora) && !vencida.tienePagoActivo()) {
                marcarExpirada(vencida);
                expiradas.add(vencida);
            }
        }
        return expiradas;
    }

    /**
     * El turno está libre: no se superpone con otra reserva que ocupe la cancha (salvo
     * {@code excluirReservaId}) ni con un turno fijo que todavía no tiene su partido de esa fecha.
     */
    void validarLibre(Long canchaId, LocalDate fecha, LocalTime inicio, LocalTime fin, LocalDateTime ahora,
            Long excluirReservaId) {
        validarLibre(canchaId, fecha, inicio, fin, ahora, excluirReservaId, null);
    }

    /** @param excluirTurnoFijoId el turno fijo cuyo partido se está creando o modificando (no se bloquea a sí mismo) */
    void validarLibre(Long canchaId, LocalDate fecha, LocalTime inicio, LocalTime fin, LocalDateTime ahora,
            Long excluirReservaId, Long excluirTurnoFijoId) {
        boolean ocupado = buscarQueOcupan(canchaId, fecha, inicio, fin, ahora).stream()
                .anyMatch(otra -> !otra.getId().equals(excluirReservaId));
        if (ocupado) {
            throw new HorarioOcupadoException();
        }
        if (turnosFijosQueBloquean(canchaId, fecha, inicio, fin).stream()
                .anyMatch(tf -> !tf.getId().equals(excluirTurnoFijoId))) {
            throw new HorarioOcupadoException("Ese horario es un turno fijo del club en esa cancha.");
        }
    }

    /** Turnos fijos activos que ocupan el horario ese día y todavía no tienen su partido creado. */
    public List<TurnoFijo> turnosFijosQueBloquean(Long canchaId, LocalDate fecha, LocalTime inicio, LocalTime fin) {
        return turnoFijoRepository.findQueBloquean(canchaId, fecha.getDayOfWeek(), fecha, inicio, fin);
    }

    /** Guarda y fuerza el INSERT/UPDATE para que la restricción de la BD detecte solapamientos concurrentes. */
    Reserva guardarVerificandoSolapamiento(Reserva reserva) {
        try {
            return reservaRepository.saveAndFlush(reserva);
        } catch (DataIntegrityViolationException e) {
            log.warn("Solapamiento detectado por la base de datos para la cancha {} el {}",
                    reserva.getCancha().getId(), reserva.getFechaReserva());
            throw new HorarioOcupadoException();
        }
    }

    /**
     * Reservas que ocupan el turno: todas menos CANCELADA, EXPIRADA y PENDIENTE vencidas
     * sin pagos activos. Es la misma definición que usa la disponibilidad.
     */
    public List<Reserva> buscarQueOcupan(Long canchaId, LocalDate fecha, LocalTime inicio, LocalTime fin,
            LocalDateTime ahora) {
        return reservaRepository.findQueOcupan(canchaId, fecha, inicio, fin, ahora,
                ESTADOS_INACTIVOS, EstadoReserva.PENDIENTE, PAGOS_ACTIVOS);
    }

    /** Valida fecha y horario contra la configuración. Devuelve la duración en minutos. */
    long validarHorario(Configuracion config, LocalDate fecha, LocalTime inicio, LocalTime fin,
            LocalDateTime ahora) {
        if (fecha.isBefore(ahora.toLocalDate())) {
            throw new ReservaException("La fecha debe ser hoy o en el futuro.");
        }
        if (fecha.isEqual(ahora.toLocalDate()) && inicio.isBefore(ahora.toLocalTime())) {
            throw new ReservaException("No podés reservar un horario que ya pasó.");
        }
        if (!inicio.isBefore(fin)) {
            throw new ReservaException("La hora de inicio debe ser anterior a la hora de fin.");
        }

        long duracion = Duration.between(inicio, fin).toMinutes();
        configuracionService.validarDuracion(config, duracion);

        DiaApertura dia = configuracionService.obtenerDiaApertura(config, fecha);
        if (inicio.isBefore(dia.getHorario_inicio())) {
            throw new DiaSinAperturaException(
                    "El horario de apertura el %s es a las %s.".formatted(dia.getDia(), dia.getHorario_inicio()));
        }
        if (fin.isAfter(dia.getHorario_fin())) {
            throw new DiaSinAperturaException(
                    "El horario de cierre el %s es a las %s.".formatted(dia.getDia(), dia.getHorario_fin()));
        }
        if (Duration.between(dia.getHorario_inicio(), inicio).toMinutes()
                % ConfiguracionService.MINUTOS_POR_BLOQUE != 0) {
            throw new ReservaException(
                    "El horario de inicio debe coincidir con un turno válido (cada 30 minutos desde la apertura).");
        }
        return duracion;
    }

    private Usuario resolverCliente(Long clienteId, Usuario solicitante) {
        return switch (solicitante.getRol()) {
            case CLIENTE -> {
                if (clienteId != null && !clienteId.equals(solicitante.getId())) {
                    throw new ReservaException("Un cliente solo puede reservar para sí mismo.");
                }
                yield solicitante;
            }
            case ADMIN, EMPLEADO -> {
                if (clienteId == null) {
                    throw new ReservaException("ADMIN y EMPLEADO deben indicar el clienteId de la reserva.");
                }
                Usuario cliente = usuarioService.findByIdOrThrow(clienteId);
                if (cliente.getRol() != Role.CLIENTE) {
                    throw new ReservaException("El usuario indicado no es un cliente.");
                }
                if (!cliente.getEnabled()) {
                    throw new ReservaException("La cuenta del cliente está desactivada.");
                }
                yield cliente;
            }
            case PROFESOR -> throw new ReservaException("Los profesores no pueden crear reservas de canchas.");
        };
    }

    private void validarPerfilCompleto(Usuario cliente, Usuario solicitante) {
        ClienteProfile perfil = clienteProfileService.findByUsuario(cliente);
        if (!clienteProfileService.esPerfilCompleto(perfil)) {
            throw new PerfilIncompletoException(solicitante.getRol() == Role.CLIENTE
                    ? "Debés completar tu perfil antes de reservar."
                    : "El cliente %s %s debe completar su perfil antes de reservar."
                            .formatted(cliente.getNombre(), cliente.getApellido()));
        }
    }

    // ── Cancelación y expiración ─────────────────────────────────────────

    /** Expiración pedida por el usuario (por ejemplo, cuando el contador del front llega a cero). */
    @Transactional
    public void expirarPorUsuario(Long id, String email) {
        Usuario usuario = usuarioService.findByEmailOrThrow(email,
                new CredencialesInvalidasException("Usuario no encontrado"));
        Reserva reserva = bloquearConAcceso(id, usuario);
        LocalDateTime ahora = LocalDateTime.now(clock);

        if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
            throw new EstadoInvalidoException("Solo se pueden expirar reservas PENDIENTE.");
        }
        if (!reserva.estaVencida(ahora)) {
            throw new EstadoInvalidoException("La reserva todavía no venció.");
        }
        if (reserva.tienePagoActivo()) {
            log.info("Reserva #{} no expirada: tiene un pago activo", id);
            return;
        }
        expirar(reserva);
    }

    /**
     * Expira la reserva si sigue PENDIENTE, venció y no tiene pagos aprobados ni en proceso.
     * La usan el job y la creación de reservas.
     *
     * @return true si la expiró
     */
    @Transactional
    public boolean expirarSiCorresponde(Long id) {
        Optional<Reserva> encontrada = reservaRepository.findByIdForUpdate(id);
        if (encontrada.isEmpty()) {
            return false;
        }
        Reserva reserva = encontrada.get();
        if (!reserva.estaVencida(LocalDateTime.now(clock)) || reserva.tienePagoActivo()) {
            return false;
        }
        expirar(reserva);
        return true;
    }

    public List<Long> idsPendientesVencidas() {
        return reservaRepository.findIdsPendientesVencidas(EstadoReserva.PENDIENTE, LocalDateTime.now(clock));
    }

    /**
     * Pasa a COMPLETADA las reservas PAGADA cuyo turno ya terminó. Las señadas o parciales
     * quedan como están hasta que se cobre el saldo (ese pago las completa).
     */
    @Transactional
    public int marcarCompletadas() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        return reservaRepository.marcarCompletadas(EstadoReserva.COMPLETADA, Set.of(EstadoReserva.PAGADA),
                ahora.toLocalDate(), ahora.toLocalTime());
    }

    // ── Devolución de productos alquilados ───────────────────────────────

    /**
     * El personal registra cuántas paletas devolvieron. Las que faltan (rotas o perdidas) se
     * descuentan del stock total. Hasta registrarla, las paletas de un turno terminado cuentan
     * como afuera del club.
     */
    @Transactional
    public ReservaResponse registrarDevolucion(Long id, int paletasDevueltas, String emailPersonal) {
        Reserva reserva = bloquear(id);
        LocalDateTime ahora = LocalDateTime.now(clock);

        if (!Reserva.ESTADOS_QUE_SE_JUEGAN.contains(reserva.getEstado())) {
            throw new EstadoInvalidoException("La reserva está " + reserva.getEstado() + ": no se entregaron paletas.");
        }
        if (!reserva.turnoEmpezo(ahora)) {
            throw new EstadoInvalidoException("El turno todavía no empezó: las paletas no se entregaron.");
        }
        ReservaProducto item = reserva.getProductos().stream()
                .filter(i -> Producto.CODIGO_PALETAS.equals(i.getProducto().getCodigo()))
                .findFirst()
                .orElseThrow(() -> new EstadoInvalidoException("La reserva no tiene paletas alquiladas."));
        if (item.devolucionRegistrada()) {
            throw new EstadoInvalidoException("La devolución de paletas ya fue registrada.");
        }
        if (paletasDevueltas < 0 || paletasDevueltas > item.getCantidad()) {
            throw new ReservaException("Se alquilaron %d paletas: la cantidad devuelta tiene que estar entre 0 y %d."
                    .formatted(item.getCantidad(), item.getCantidad()));
        }

        productoService.registrarDevolucion(item, paletasDevueltas, ahora);
        log.info("Reserva #{}: {} de {} paletas devueltas (registró {})", id, paletasDevueltas, item.getCantidad(),
                emailPersonal);
        return aResponse(reserva);
    }

    /** Reservas cuyo turno ya empezó y todavía tienen paletas sin devolver. */
    @Transactional(readOnly = true)
    public List<ReservaResponse> devolucionesPendientes() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        return reservaRepository.findConAlquileresSinDevolver(TipoProducto.ALQUILER, Reserva.ESTADOS_QUE_SE_JUEGAN,
                ahora.toLocalDate(), ahora.toLocalTime()).stream()
                .map(this::aResponse)
                .toList();
    }

    /**
     * Turnos ya jugados que deben saldo (SEÑADA o PARCIALMENTE_PAGADA con el turno terminado),
     * del más viejo al más nuevo. Al registrar el pago del saldo pasan a COMPLETADA.
     */
    @Transactional(readOnly = true)
    public List<ReservaResponse> saldosPendientes() {
        LocalDateTime ahora = LocalDateTime.now(clock);
        return reservaRepository.findTerminadasConSaldo(
                Reserva.ESTADOS_CON_SALDO,
                ahora.toLocalDate(), ahora.toLocalTime()).stream()
                .map(this::aResponse)
                .toList();
    }

    /** Expira la reserva (ya bloqueada) y devuelve su stock. */
    private void expirar(Reserva reserva) {
        marcarExpirada(reserva);
        productoService.liberar(reserva);
    }

    /** Cancela la reserva (ya bloqueada) y devuelve su stock. Los reembolsos los hace CancelacionService. */
    void cancelar(Reserva reserva) {
        marcarCancelada(reserva);
        productoService.liberar(reserva);
    }

    private void marcarExpirada(Reserva reserva) {
        reserva.setEstado(EstadoReserva.EXPIRADA);
        expirarPagosPendientes(reserva);
        reservaRepository.save(reserva);
        log.info("Reserva #{} expirada", reserva.getId());
    }

    private void marcarCancelada(Reserva reserva) {
        reserva.setEstado(EstadoReserva.CANCELADA);
        expirarPagosPendientes(reserva);
        reservaRepository.save(reserva);
    }

    /** Los pagos iniciados y nunca cobrados de la reserva quedan EXPIRADO (ya no se pueden cobrar). */
    private void expirarPagosPendientes(Reserva reserva) {
        for (Pago pago : reserva.getPagos()) {
            if (pago.getEstado() == EstadoPago.PENDIENTE) {
                pago.setEstado(EstadoPago.EXPIRADO);
                pagoRepository.save(pago);
            }
        }
    }

    // ── Respuesta ────────────────────────────────────────────────────────

    public ReservaResponse aResponse(Reserva reserva) {
        ReservaResponse response = ReservaResponse.from(reserva);
        response.setMontoSenia(configuracionService.calcularSenia(configuracionService.obtener(),
                reserva.getMontoTotal()));

        // si el cliente la cancelara ahora: si puede, hasta cuándo y cuánto se le devolvería
        PoliticaCancelacion.Evaluacion cancelacion = politicaCancelacion.evaluarParaCliente(reserva,
                LocalDateTime.now(clock));
        response.setCancelable(cancelacion.cancelable());
        response.setCancelableHasta(cancelacion.cancelableHasta());
        response.setMontoReembolso(cancelacion.montoReembolso());
        response.setReembolsoTotalHasta(cancelacion.reembolsoTotalHasta());
        response.setReembolsoSeniaHasta(cancelacion.reembolsoSeniaHasta());

        // turno jugado pero sin pagar del todo: el front lo muestra como "Jugada · saldo pendiente"
        boolean turnoTerminado = reserva.turnoTermino(LocalDateTime.now(clock));
        response.setTurnoTerminado(turnoTerminado);
        response.setDebeSaldo(turnoTerminado && Reserva.ESTADOS_CON_SALDO.contains(reserva.getEstado()));
        return response;
    }

    /** La respuesta para quien la pide: a un cliente no se le muestran los teléfonos de los otros jugadores. */
    ReservaResponse aResponsePara(Reserva reserva, Usuario usuario) {
        ReservaResponse response = aResponse(reserva);
        if (!esPersonal(usuario)) {
            response.getJugadores().forEach(j -> j.setTelefono(null));
        }
        return response;
    }
}
