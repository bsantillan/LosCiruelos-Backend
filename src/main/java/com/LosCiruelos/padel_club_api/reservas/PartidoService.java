package com.LosCiruelos.padel_club_api.reservas;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.Services.ConfiguracionService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;
import com.LosCiruelos.padel_club_api.pagos.PagoService;
import com.LosCiruelos.padel_club_api.reservas.dto.JugadorRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.PartidoRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.ReservaResponse;
import com.LosCiruelos.padel_club_api.Entities.Cancha;
import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.pagos.Pago;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.CanchaNotFoundException;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Exceptions.UsuarioNotFoundException;
import com.LosCiruelos.padel_club_api.Repository.CanchaRepository;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Roles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Partidos de 4 jugadores que arma el ADMIN (sueltos o de un turno fijo). Son reservas tipo
 * PARTIDO: no vencen (sin nada pagado quedan CONFIRMADA), su "cliente" es el ADMIN que las armó y
 * cada jugador paga su parte en el club. Mismas reglas de horario, solapamiento y stock que una
 * reserva normal, con la cancha bloqueada mientras se crea o se cambia el turno.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartidoService {

    private final ReservaService reservaService;
    private final DisponibilidadService disponibilidad;
    private final ReservaMapper reservaMapper;
    private final PagoService pagoService;
    private final UsuarioService usuarioService;
    private final ConfiguracionService configuracionService;
    private final StockService stockService;
    private final CanchaRepository canchaRepository;
    private final ReservaRepository reservaRepository;
    private final TurnoFijoRepository turnoFijoRepository;
    private final Reloj reloj;

    /** Un jugador ya validado: un cliente registrado o un invitado (nombre y teléfono opcional). */
    public record DatosJugador(Usuario usuario, String nombre, String telefono) {

        boolean mismaPersonaQue(Usuario otroUsuario, String otroNombre) {
            if (usuario != null || otroUsuario != null) {
                return usuario != null && otroUsuario != null && usuario.getId().equals(otroUsuario.getId());
            }
            return nombre.equalsIgnoreCase(otroNombre);
        }
    }

    // ── Crear ────────────────────────────────────────────────────────────

    @Transactional
    public ReservaResponse crear(PartidoRequest req, String email) {
        Usuario admin = admin(email);
        LocalDateTime ahora = reloj.ahora();
        Configuracion config = configuracionService.obtener();
        long duracion = disponibilidad.validarHorario(config, req.getFechaReserva(), req.getHoraInicio(),
                req.getHoraFin(), ahora);
        List<DatosJugador> jugadores = resolverJugadores(req.getJugadores());

        Reserva partido = crearPartido(admin, req.getCanchaId(), req.getFechaReserva(), req.getHoraInicio(),
                req.getHoraFin(), jugadores, req.getCantidadPelotas(), req.getCantidadPaletas(), null, config,
                duracion, ahora);
        log.info("Partido #{} armado por {} en la cancha {} el {} de {} a {}", partido.getId(), email,
                partido.getCancha().getNumero(), partido.getFechaReserva(), partido.getHoraInicio(),
                partido.getHoraFin());
        return reservaMapper.aResponse(partido);
    }

    /**
     * Crea el partido de un turno fijo para esa fecha, en su propia transacción y con el turno fijo
     * bloqueado. No hace nada si el turno fijo ya no está activo, no se juega ese día o ya tiene su
     * partido (o la fecha se dio de baja). Si el horario no se puede usar (ocupado, el club no abre,
     * falta stock) lanza la excepción y no se crea nada.
     *
     * @return el id del partido creado
     */
    @Transactional
    public Optional<Long> crearParaTurnoFijo(Long turnoFijoId, LocalDate fecha) {
        TurnoFijo tf = turnoFijoRepository.findByIdForUpdate(turnoFijoId).orElse(null);
        if (tf == null || !Boolean.TRUE.equals(tf.getActivo()) || !tf.seJuegaEl(fecha)
                || reservaRepository.existsByTurnoFijoAndFechaReserva(tf, fecha)) {
            return Optional.empty();
        }
        LocalDateTime ahora = reloj.ahora();
        Configuracion config = configuracionService.obtener();
        long duracion = disponibilidad.validarHorario(config, fecha, tf.getHoraInicio(), tf.getHoraFin(), ahora);
        List<DatosJugador> jugadores = tf.getJugadores().stream()
                .map(j -> new DatosJugador(j.getUsuario(), j.getNombre(), j.getTelefono()))
                .toList();

        Reserva partido = crearPartido(tf.getCreatedBy(), tf.getCancha().getId(), fecha, tf.getHoraInicio(),
                tf.getHoraFin(), jugadores, tf.getCantidadPelotas(), tf.getCantidadPaletas(), tf, config, duracion,
                ahora);
        log.info("Partido #{} del turno fijo #{} creado para el {}", partido.getId(), tf.getId(), fecha);
        return Optional.of(partido.getId());
    }

    private Reserva crearPartido(Usuario admin, Long canchaId, LocalDate fecha, LocalTime inicio, LocalTime fin,
            List<DatosJugador> jugadores, Integer pelotas, Integer paletas, TurnoFijo turnoFijo,
            Configuracion config, long duracion, LocalDateTime ahora) {
        // lock de la cancha: los pedidos simultáneos para esta cancha esperan su turno
        Cancha cancha = canchaRepository.findByIdForUpdate(canchaId)
                .orElseThrow(() -> new CanchaNotFoundException("Cancha no encontrada: " + canchaId));
        List<Reserva> aLiberar = reservaService.expirarVencidasQueSeSuperponen(cancha.getId(), fecha, inicio, fin,
                ahora);
        disponibilidad.validarLibre(cancha.getId(), fecha, inicio, fin, ahora, null,
                turnoFijo != null ? turnoFijo.getId() : null);

        Reserva partido = Reserva.builder()
                .cancha(cancha)
                .cliente(admin)
                .createdBy(admin)
                .fechaReserva(fecha)
                .horaInicio(inicio)
                .horaFin(fin)
                .estado(EstadoReserva.CONFIRMADA)
                .tipo(TipoReserva.PARTIDO)
                .turnoFijo(turnoFijo)
                .createdAt(ahora)
                .build();
        for (int i = 0; i < jugadores.size(); i++) {
            DatosJugador datos = jugadores.get(i);
            partido.getJugadores().add(ReservaJugador.builder()
                    .reserva(partido)
                    .posicion(i + 1)
                    .usuario(datos.usuario())
                    .nombre(datos.nombre())
                    .telefono(datos.telefono())
                    .build());
        }

        stockService.moverStock(aLiberar, partido, ReservaService.pedidoDeProductos(pelotas, paletas), ahora);
        partido.setMontoTotal(configuracionService.calcularMontoTurno(config, duracion)
                .add(partido.getMontoProductos()));
        return reservaService.guardarVerificandoSolapamiento(partido);
    }

    // ── Modificar ────────────────────────────────────────────────────────

    /**
     * Cambia jugadores, cancha, horario o productos de un partido que todavía no se jugó. No se
     * puede reemplazar a un jugador que ya pagó (o tiene una bonificación), y el total nuevo no puede
     * quedar por debajo de lo ya pagado. Si no cambia el turno, se mantiene el precio de la cancha.
     */
    @Transactional
    public ReservaResponse actualizar(Long id, PartidoRequest req, String email) {
        admin(email);
        Reserva partido = reservaService.bloquear(id);
        LocalDateTime ahora = reloj.ahora();
        if (!partido.esPartido()) {
            throw new ReservaException("La reserva #" + id + " no es un partido.");
        }
        if (!Reserva.ESTADOS_CONFIRMADOS.contains(partido.getEstado())) {
            throw new EstadoInvalidoException("No se puede modificar un partido " + partido.getEstado() + ".");
        }
        if (partido.turnoTermino(ahora)) {
            throw new EstadoInvalidoException("El partido ya se jugó: no se puede modificar.");
        }
        List<DatosJugador> jugadores = resolverJugadores(req.getJugadores());
        Configuracion config = configuracionService.obtener();

        boolean cambiaTurno = !partido.getCancha().getId().equals(req.getCanchaId())
                || !partido.getFechaReserva().equals(req.getFechaReserva())
                || !partido.getHoraInicio().equals(req.getHoraInicio())
                || !partido.getHoraFin().equals(req.getHoraFin());
        BigDecimal montoCancha = partido.getMontoCancha();
        List<Reserva> aLiberar = new ArrayList<>();
        if (cambiaTurno) {
            if (partido.getTurnoFijo() != null && !partido.getFechaReserva().equals(req.getFechaReserva())) {
                throw new ReservaException("Un partido de un turno fijo no cambia de fecha: dá de baja esa fecha "
                        + "del turno fijo y armá un partido aparte.");
            }
            if (partido.turnoEmpezo(ahora)) {
                throw new EstadoInvalidoException("El partido ya empezó: no se puede cambiar el turno.");
            }
            long duracion = disponibilidad.validarHorario(config, req.getFechaReserva(), req.getHoraInicio(),
                    req.getHoraFin(), ahora);
            Cancha cancha = canchaRepository.findByIdForUpdate(req.getCanchaId())
                    .orElseThrow(() -> new CanchaNotFoundException("Cancha no encontrada: " + req.getCanchaId()));
            aLiberar.addAll(reservaService.expirarVencidasQueSeSuperponen(cancha.getId(), req.getFechaReserva(),
                    req.getHoraInicio(), req.getHoraFin(), ahora));
            disponibilidad.validarLibre(cancha.getId(), req.getFechaReserva(), req.getHoraInicio(),
                    req.getHoraFin(), ahora, partido.getId(),
                    partido.getTurnoFijo() != null ? partido.getTurnoFijo().getId() : null);
            partido.setCancha(cancha);
            partido.setFechaReserva(req.getFechaReserva());
            partido.setHoraInicio(req.getHoraInicio());
            partido.setHoraFin(req.getHoraFin());
            montoCancha = configuracionService.calcularMontoTurno(config, duracion);
        }

        actualizarJugadores(partido, jugadores);
        // devuelve el stock de los productos anteriores y toma los nuevos (los alquileres se
        // vuelven a validar porque pudo cambiar el horario)
        stockService.moverStock(aLiberar, partido,
                ReservaService.pedidoDeProductos(req.getCantidadPelotas(), req.getCantidadPaletas()), ahora);
        BigDecimal total = montoCancha.add(partido.getMontoProductos());
        if (total.compareTo(partido.getMontoCubierto()) < 0) {
            throw new EstadoInvalidoException("El total nuevo ($%s) es menor que lo ya pagado ($%s)."
                    .formatted(total, partido.getMontoCubierto()));
        }
        partido.setMontoTotal(total);
        // el total pudo cambiar: el estado se vuelve a calcular con lo pagado (sin avisos)
        pagoService.recalcularEstadoReserva(partido, false);
        partido = reservaService.guardarVerificandoSolapamiento(partido);
        log.info("Partido #{} modificado por {}", id, email);
        return reservaMapper.aResponse(partido);
    }

    /**
     * Los jugadores se actualizan en su lugar (posición por posición). Un jugador que ya pagó o
     * tiene una bonificación no se puede reemplazar: sus pagos quedarían a nombre de otro.
     */
    private static void actualizarJugadores(Reserva partido, List<DatosJugador> nuevos) {
        for (int i = 0; i < nuevos.size(); i++) {
            int posicion = i + 1;
            DatosJugador nuevo = nuevos.get(i);
            ReservaJugador actual = partido.getJugadores().stream()
                    .filter(j -> j.getPosicion() == posicion)
                    .findFirst()
                    .orElse(null);
            if (actual == null) {
                partido.getJugadores().add(ReservaJugador.builder().reserva(partido).posicion(posicion)
                        .usuario(nuevo.usuario()).nombre(nuevo.nombre()).telefono(nuevo.telefono()).build());
                continue;
            }
            if (!nuevo.mismaPersonaQue(actual.getUsuario(), actual.getNombre()) && tienePagos(partido, actual)) {
                throw new EstadoInvalidoException(actual.nombreParaMostrar()
                        + " ya pagó o tiene una bonificación: no se lo puede reemplazar.");
            }
            actual.setUsuario(nuevo.usuario());
            actual.setNombre(nuevo.nombre());
            actual.setTelefono(nuevo.telefono());
        }
    }

    private static boolean tienePagos(Reserva partido, ReservaJugador jugador) {
        return partido.getPagos().stream().anyMatch(p -> p.getJugador() != null
                && p.getJugador().getId().equals(jugador.getId())
                && Pago.ESTADOS_CON_DINERO.contains(p.getEstado()));
    }

    // ── Jugadores ────────────────────────────────────────────────────────

    /**
     * Valida los 4 jugadores: cada uno es un cliente registrado y activo (usuarioId) o un invitado
     * (nombre, y teléfono opcional). Un mismo cliente no puede estar dos veces.
     */
    public List<DatosJugador> resolverJugadores(List<JugadorRequest> pedidos) {
        if (pedidos == null || pedidos.size() != Jugadores.POR_PARTIDO) {
            throw new ReservaException("Un partido tiene " + Jugadores.POR_PARTIDO + " jugadores.");
        }
        List<DatosJugador> jugadores = new ArrayList<>();
        Set<Long> usuarios = new HashSet<>();
        for (int i = 0; i < pedidos.size(); i++) {
            JugadorRequest pedido = pedidos.get(i);
            int posicion = i + 1;
            if (pedido == null) {
                throw new ReservaException("Falta el jugador " + posicion + ".");
            }
            if (pedido.getUsuarioId() != null) {
                Usuario usuario;
                try {
                    usuario = usuarioService.findByIdOrThrow(pedido.getUsuarioId());
                } catch (UsuarioNotFoundException e) {
                    throw new ReservaException("El jugador " + posicion + " no existe (usuario "
                            + pedido.getUsuarioId() + ").");
                }
                if (usuario.getRol() != Role.CLIENTE) {
                    throw new ReservaException("El jugador " + posicion + " no es un cliente.");
                }
                if (!Boolean.TRUE.equals(usuario.getEnabled())) {
                    throw new ReservaException("La cuenta del jugador " + posicion + " está desactivada.");
                }
                if (!usuarios.add(usuario.getId())) {
                    throw new ReservaException("El mismo cliente no puede estar dos veces en el partido.");
                }
                jugadores.add(new DatosJugador(usuario, null, null));
            } else {
                String nombre = pedido.getNombre() != null ? pedido.getNombre().trim() : "";
                if (nombre.isEmpty()) {
                    throw new ReservaException("Indicá el cliente (usuarioId) o el nombre del jugador " + posicion
                            + ".");
                }
                String telefono = pedido.getTelefono() != null && !pedido.getTelefono().isBlank()
                        ? pedido.getTelefono().trim()
                        : null;
                jugadores.add(new DatosJugador(null, nombre, telefono));
            }
        }
        return jugadores;
    }

    private Usuario admin(String email) {
        Usuario usuario = usuarioService.autenticado(email);
        Roles.exigirAdmin(usuario, "Solo el ADMIN puede armar partidos.");
        return usuario;
    }
}
