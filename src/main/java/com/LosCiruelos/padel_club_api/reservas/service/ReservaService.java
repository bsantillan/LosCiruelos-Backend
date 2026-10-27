package com.LosCiruelos.padel_club_api.reservas.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.Services.ClienteProfileService;
import com.LosCiruelos.padel_club_api.Services.ConfiguracionService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;
import com.LosCiruelos.padel_club_api.reservas.dto.request.CrearReservaAdminRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.request.CrearReservaRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.Entities.Cancha;
import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.Entities.Producto;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.CanchaNotFoundException;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.HorarioOcupadoException;
import com.LosCiruelos.padel_club_api.Exceptions.PerfilIncompletoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaNoEncontradaException;
import com.LosCiruelos.padel_club_api.Repository.CanchaRepository;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaProducto;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.mapper.ReservaMapper;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ciclo de vida de las reservas: crear, cambiar una pendiente, expirar, completar y registrar la
 * devolución de paletas. Las consultas están en {@link ReservaConsultaService}.
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

    private final ConfiguracionService configuracionService;
    private final UsuarioService usuarioService;
    private final ClienteProfileService clienteProfileService;
    private final ReservaRepository reservaRepository;
    private final CanchaRepository canchaRepository;
    private final PagoRepository pagoRepository;
    private final StockService stockService;
    private final ReservaMapper mapper;
    private final DisponibilidadService disponibilidad;
    private final Reloj reloj;

    // ── Bloqueo y acceso ─────────────────────────────────────────────────

    /** Carga la reserva con lock de escritura. Debe ser lo primero que se lee de ella en la transacción. */
    public Reserva bloquear(Long id) {
        return reservaRepository.findByIdForUpdate(id).orElseThrow(() -> new ReservaNoEncontradaException(id));
    }

    public Reserva bloquearConAcceso(Long id, Usuario usuario) {
        Reserva reserva = bloquear(id);
        if (!reserva.puedeOperarla(usuario)) {
            throw new ReservaNoEncontradaException(id);
        }
        return reserva;
    }

    // ── Creación ─────────────────────────────────────────────────────────

    /** El cliente reserva para sí. */
    @Transactional
    public ReservaResponse crearParaMi(CrearReservaRequest req, String email) {
        Usuario cliente = usuarioService.autenticado(email);
        validarPerfilCompleto(cliente, "Debés completar tu perfil antes de reservar.");
        return crear(req, cliente, cliente);
    }

    /** El personal (ADMIN o EMPLEADO) carga la reserva de un cliente. */
    @Transactional
    public ReservaResponse crearParaCliente(CrearReservaAdminRequest req, String emailPersonal) {
        Usuario personal = usuarioService.autenticado(emailPersonal);
        Usuario cliente = clienteDeLaReserva(req.getClienteId());
        validarPerfilCompleto(cliente, "El cliente %s %s debe completar su perfil antes de reservar."
                .formatted(cliente.getNombre(), cliente.getApellido()));
        return crear(req, cliente, personal);
    }

    private ReservaResponse crear(CrearReservaRequest req, Usuario cliente, Usuario solicitante) {
        LocalDateTime ahora = reloj.ahora();
        Configuracion config = configuracionService.obtener();
        long duracion = disponibilidad.validarHorario(config, req.getFechaReserva(), req.getHoraInicio(), req.getHoraFin(), ahora);

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
            return mapper.aResponse(actualizarPendiente(bloquear(existente.get()), req, duracion, config, ahora, aLiberar));
        }

        // 3. Otras PENDIENTE del cliente en esa cancha y fecha sin pagos activos: eligió otro horario
        for (Long id : reservaRepository.findPendientesSinPagoActivo(cliente.getId(), cancha.getId(),
                req.getFechaReserva(), ahora, EstadoReserva.PENDIENTE, DisponibilidadService.PAGOS_ACTIVOS)) {
            Reserva anterior = bloquear(id);
            if (anterior.estaPendienteVigente(ahora) && !anterior.tienePagoActivo()) {
                marcarCancelada(anterior);
                aLiberar.add(anterior);
                log.info("Reserva #{} cancelada automáticamente: el cliente eligió otro horario", id);
            }
        }

        // 4. Solapamiento con otras reservas y con los turnos fijos
        disponibilidad.validarLibre(cancha.getId(), req.getFechaReserva(), req.getHoraInicio(), req.getHoraFin(), ahora, null);

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
        stockService.moverStock(aLiberar, reserva, pedidoDeProductos(req), ahora);
        reserva.setMontoTotal(configuracionService.calcularMontoTurno(config, duracion).add(reserva.getMontoProductos()));

        reserva = guardarVerificandoSolapamiento(reserva);
        log.info("Reserva #{} creada para el cliente {} en la cancha {} el {} de {} a {}",
                reserva.getId(), cliente.getId(), cancha.getNumero(), reserva.getFechaReserva(),
                reserva.getHoraInicio(), reserva.getHoraFin());
        return mapper.aResponse(reserva);
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
            stockService.moverStock(aLiberar, null, Map.of(), ahora);
            return reserva;
        }

        if (cambiaFin) {
            disponibilidad.validarLibre(reserva.getCancha().getId(), reserva.getFechaReserva(), req.getHoraInicio(),
                    req.getHoraFin(), ahora, reserva.getId());
            reserva.setHoraFin(req.getHoraFin());
        }

        // devuelve el stock de los productos anteriores y toma los nuevos (los alquileres se
        // vuelven a validar porque pudo cambiar el horario)
        stockService.moverStock(aLiberar, reserva, pedido, ahora);

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
                EstadoReserva.PENDIENTE, DisponibilidadService.PAGOS_ACTIVOS)) {
            Reserva vencida = bloquear(id);
            if (vencida.estaVencida(ahora) && !vencida.tienePagoActivo()) {
                marcarExpirada(vencida);
                expiradas.add(vencida);
            }
        }
        return expiradas;
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

    private Usuario clienteDeLaReserva(Long clienteId) {
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
        return cliente;
    }

    private void validarPerfilCompleto(Usuario cliente, String mensaje) {
        ClienteProfile perfil = clienteProfileService.findByUsuario(cliente);
        if (!clienteProfileService.esPerfilCompleto(perfil)) {
            throw new PerfilIncompletoException(mensaje);
        }
    }

    // ── Cancelación y expiración ─────────────────────────────────────────

    /** Expiración pedida por el usuario (por ejemplo, cuando el contador del front llega a cero). */
    @Transactional
    public void expirarPorUsuario(Long id, String email) {
        Usuario usuario = usuarioService.autenticado(email);
        Reserva reserva = bloquearConAcceso(id, usuario);
        LocalDateTime ahora = reloj.ahora();

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
        if (!reserva.estaVencida(reloj.ahora()) || reserva.tienePagoActivo()) {
            return false;
        }
        expirar(reserva);
        return true;
    }

    public List<Long> idsPendientesVencidas() {
        return reservaRepository.findIdsPendientesVencidas(EstadoReserva.PENDIENTE, reloj.ahora());
    }

    /**
     * Pasa a COMPLETADA las reservas PAGADA cuyo turno ya terminó. Las señadas o parciales
     * quedan como están hasta que se cobre el saldo (ese pago las completa).
     */
    @Transactional
    public int marcarCompletadas() {
        LocalDateTime ahora = reloj.ahora();
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
        LocalDateTime ahora = reloj.ahora();

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

        stockService.registrarDevolucion(item, paletasDevueltas, ahora);
        log.info("Reserva #{}: {} de {} paletas devueltas (registró {})", id, paletasDevueltas, item.getCantidad(),
                emailPersonal);
        return mapper.aResponse(reserva);
    }

    /** Expira la reserva (ya bloqueada) y devuelve su stock. */
    private void expirar(Reserva reserva) {
        marcarExpirada(reserva);
        stockService.liberar(reserva);
    }

    /** Cancela la reserva (ya bloqueada) y devuelve su stock. Los reembolsos los hace CancelacionService. */
    void cancelar(Reserva reserva) {
        marcarCancelada(reserva);
        stockService.liberar(reserva);
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
        reserva.expirarPagosPendientes().forEach(pagoRepository::save);
    }
}
