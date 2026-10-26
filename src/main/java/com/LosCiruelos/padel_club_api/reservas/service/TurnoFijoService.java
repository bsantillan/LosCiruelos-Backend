package com.LosCiruelos.padel_club_api.reservas.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.Services.ConfiguracionService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;
import com.LosCiruelos.padel_club_api.reservas.dto.request.TurnoFijoRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.TurnoFijoResponse;
import com.LosCiruelos.padel_club_api.Entities.Cancha;
import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Exceptions.CanchaNotFoundException;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.HorarioOcupadoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Exceptions.TurnoFijoNoEncontradoException;
import com.LosCiruelos.padel_club_api.Repository.CanchaRepository;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijo;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijoJugador;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.OpcionReembolso;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.TipoReserva;
import com.LosCiruelos.padel_club_api.reservas.mapper.ReservaMapper;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnoFijoRepository;
import com.LosCiruelos.padel_club_api.reservas.service.PartidoService.DatosJugador;
import com.LosCiruelos.padel_club_api.common.Fechas;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.common.Turno;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Turnos fijos del ADMIN: el mismo partido todas las semanas. El horario queda bloqueado siempre
 * (ver {@link DisponibilidadService#turnosFijosQueBloquean}); los partidos de cada semana los crea
 * {@link GeneradorTurnosFijos}. Toda operación bloquea primero el turno fijo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TurnoFijoService {

    private final TurnoFijoRepository turnoFijoRepository;
    private final ReservaRepository reservaRepository;
    private final CanchaRepository canchaRepository;
    private final ReservaService reservaService;
    private final DisponibilidadService disponibilidad;
    private final ReservaMapper reservaMapper;
    private final PartidoService partidoService;
    private final CancelacionService cancelacionService;
    private final StockService stockService;
    private final ConfiguracionService configuracionService;
    private final UsuarioService usuarioService;
    private final Reloj reloj;

    // ── Consultas ────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TurnoFijoResponse> listar(Boolean activos) {
        List<TurnoFijo> turnos = activos != null
                ? turnoFijoRepository.findByActivoOrderByDiaSemanaAscHoraInicioAsc(activos)
                : turnoFijoRepository.findAllByOrderByActivoDescDiaSemanaAscHoraInicioAsc();
        return turnos.stream().map(this::aResponse).toList();
    }

    @Transactional(readOnly = true)
    public TurnoFijoResponse obtener(Long id) {
        return aResponse(buscar(id));
    }

    // ── Crear ────────────────────────────────────────────────────────────

    /** Crea el turno fijo. Sus partidos los crea después el generador (cada uno en su transacción). */
    @Transactional
    public Long crear(TurnoFijoRequest req, String email) {
        Usuario admin = admin(email);
        LocalDateTime ahora = reloj.ahora();
        validarTurno(req, ahora, null);
        Cancha cancha = canchaRepository.findByIdForUpdate(req.getCanchaId())
                .orElseThrow(() -> new CanchaNotFoundException("Cancha no encontrada: " + req.getCanchaId()));
        List<DatosJugador> jugadores = partidoService.resolverJugadores(req.getJugadores());

        TurnoFijo tf = TurnoFijo.builder()
                .cancha(cancha)
                .diaSemana(req.getDesde().getDayOfWeek())
                .horaInicio(req.getHoraInicio())
                .horaFin(req.getHoraFin())
                .desde(req.getDesde())
                .cantidadPelotas(cantidad(req.getCantidadPelotas()))
                .cantidadPaletas(cantidad(req.getCantidadPaletas()))
                .activo(true)
                .createdBy(admin)
                .createdAt(ahora)
                .build();
        asignarJugadores(tf, jugadores);
        tf = turnoFijoRepository.save(tf);
        log.info("Turno fijo #{} creado por {}: cancha {}, los {} de {} a {} desde el {}", tf.getId(), email,
                cancha.getNumero(), dia(tf.getDiaSemana()), tf.getHoraInicio(), tf.getHoraFin(), tf.getDesde());
        return tf.getId();
    }

    // ── Modificar ────────────────────────────────────────────────────────

    /**
     * Cambia cancha, día, horario, jugadores o productos. Los partidos futuros sin pagos se borran
     * para que el generador los vuelva a crear con los datos nuevos; los que tienen pagos quedan
     * como estaban (se devuelven sus ids, para editarlos uno por uno).
     */
    @Transactional
    public List<Long> actualizar(Long id, TurnoFijoRequest req, String email) {
        admin(email);
        TurnoFijo tf = bloquearActivo(id);
        LocalDateTime ahora = reloj.ahora();
        validarTurno(req, ahora, tf);
        Cancha cancha = canchaRepository.findByIdForUpdate(req.getCanchaId())
                .orElseThrow(() -> new CanchaNotFoundException("Cancha no encontrada: " + req.getCanchaId()));
        List<DatosJugador> jugadores = partidoService.resolverJugadores(req.getJugadores());

        tf.setCancha(cancha);
        tf.setDiaSemana(req.getDesde().getDayOfWeek());
        tf.setDesde(req.getDesde());
        tf.setHoraInicio(req.getHoraInicio());
        tf.setHoraFin(req.getHoraFin());
        tf.setCantidadPelotas(cantidad(req.getCantidadPelotas()));
        tf.setCantidadPaletas(cantidad(req.getCantidadPaletas()));
        asignarJugadores(tf, jugadores);
        turnoFijoRepository.save(tf);

        List<Long> sinCambiar = new ArrayList<>();
        for (Long reservaId : reservaRepository.findIdsFuturosDelTurnoFijo(tf.getId(), ahora.toLocalDate(),
                ahora.toLocalTime())) {
            Reserva partido = reservaService.bloquear(reservaId);
            if (partido.estaInactiva()) {
                continue; // fecha dada de baja: sigue libre
            }
            if (!partido.getPagos().isEmpty()) {
                sinCambiar.add(reservaId);
                continue;
            }
            stockService.liberar(partido);
            reservaRepository.delete(partido);
        }
        reservaRepository.flush();
        log.info("Turno fijo #{} modificado por {}; partidos con pagos sin cambiar: {}", id, email, sinCambiar);
        return sinCambiar;
    }

    // ── Bajas ────────────────────────────────────────────────────────────

    /**
     * Da de baja el turno fijo: deja de bloquear el horario y se cancelan sus partidos que todavía
     * no empezaron (se avisa a los jugadores). Si alguno tiene plata cobrada hay que indicar el
     * reembolso. Los partidos ya jugados no cambian.
     */
    @Transactional
    public TurnoFijoResponse darDeBaja(Long id, OpcionReembolso reembolso, String email) {
        Usuario admin = admin(email);
        TurnoFijo tf = bloquearActivo(id);
        LocalDateTime ahora = reloj.ahora();
        tf.setActivo(false);
        tf.setDadoDeBajaAt(ahora);
        turnoFijoRepository.save(tf);

        int cancelados = 0;
        for (Long reservaId : reservaRepository.findIdsFuturosDelTurnoFijo(tf.getId(), ahora.toLocalDate(),
                ahora.toLocalTime())) {
            Reserva partido = reservaService.bloquear(reservaId);
            if (!partido.estaInactiva()) {
                cancelacionService.cancelarBloqueada(partido, admin, true, reembolso);
                cancelados++;
            }
        }
        log.info("Turno fijo #{} dado de baja por {}: {} partidos cancelados", id, email, cancelados);
        return aResponse(tf);
    }

    /**
     * Da de baja una sola fecha (por ejemplo, esa semana no vienen): se cancela su partido o, si
     * todavía no se creó, se registra la fecha como cancelada. El horario de esa fecha queda libre
     * y el generador no la vuelve a crear.
     */
    @Transactional
    public ReservaResponse darDeBajaFecha(Long id, LocalDate fecha, OpcionReembolso reembolso, String email) {
        Usuario admin = admin(email);
        TurnoFijo tf = bloquearActivo(id);
        LocalDateTime ahora = reloj.ahora();
        if (!tf.seJuegaEl(fecha)) {
            throw new ReservaException("El turno fijo no se juega el %s: es los %s desde el %s."
                    .formatted(fecha, dia(tf.getDiaSemana()), tf.getDesde()));
        }

        Reserva existente = reservaRepository.findByTurnoFijoAndFechaReserva(tf, fecha).orElse(null);
        if (existente != null) {
            Reserva partido = reservaService.bloquear(existente.getId());
            if (partido.estaInactiva()) {
                throw new EstadoInvalidoException("La fecha " + fecha + " ya está dada de baja.");
            }
            ReservaResponse cancelado = cancelacionService.cancelarBloqueada(partido, admin, true, reembolso);
            log.info("Turno fijo #{}: fecha {} dada de baja por {} (partido #{})", id, fecha, email,
                    partido.getId());
            return cancelado;
        }

        if (!fecha.atTime(tf.getHoraInicio()).isAfter(ahora)) {
            throw new EstadoInvalidoException("El turno del " + fecha + " ya empezó.");
        }
        // el partido todavía no se creó: queda registrada la fecha como cancelada
        Configuracion config = configuracionService.obtener();
        long duracion = Turno.minutosEntre(tf.getHoraInicio(), tf.getHoraFin());
        Reserva liberada = Reserva.builder()
                .cancha(tf.getCancha())
                .cliente(tf.getCreatedBy())
                .createdBy(admin)
                .fechaReserva(fecha)
                .horaInicio(tf.getHoraInicio())
                .horaFin(tf.getHoraFin())
                .montoTotal(configuracionService.calcularMontoTurno(config, duracion))
                .estado(EstadoReserva.CANCELADA)
                .tipo(TipoReserva.PARTIDO)
                .turnoFijo(tf)
                .createdAt(ahora)
                .build();
        liberada = reservaRepository.save(liberada);
        log.info("Turno fijo #{}: fecha {} dada de baja por {} antes de crear su partido", id, fecha, email);
        return reservaMapper.aResponse(liberada);
    }

    // ── Auxiliares ───────────────────────────────────────────────────────

    /**
     * Horario válido en la próxima fecha que se juega, y sin otro turno fijo activo superpuesto en
     * esa cancha y ese día. Al modificar, una fecha desde que ya pasó se acepta solo si no cambia.
     */
    private void validarTurno(TurnoFijoRequest req, LocalDateTime ahora, TurnoFijo actual) {
        LocalDate hoy = ahora.toLocalDate();
        boolean mismoDesde = actual != null && actual.getDesde().equals(req.getDesde());
        if (req.getDesde().isBefore(hoy) && !mismoDesde) {
            throw new ReservaException("La fecha desde tiene que ser hoy o en el futuro.");
        }
        DayOfWeek dia = req.getDesde().getDayOfWeek();
        Configuracion config = configuracionService.obtener();
        disponibilidad.validarHorario(config, proximaFecha(req.getDesde(), req.getHoraInicio(), ahora),
                req.getHoraInicio(), req.getHoraFin(), ahora);
        List<TurnoFijo> superpuestos = turnoFijoRepository.findSuperpuestos(actual != null ? actual.getId() : -1L,
                req.getCanchaId(), dia, req.getHoraInicio(), req.getHoraFin());
        if (!superpuestos.isEmpty()) {
            TurnoFijo otro = superpuestos.get(0);
            throw new HorarioOcupadoException("Ya hay un turno fijo en esa cancha los %s de %s a %s (#%d)."
                    .formatted(dia(dia), otro.getHoraInicio(), otro.getHoraFin(), otro.getId()));
        }
    }

    /** La primera fecha desde {@code desde} (y desde ahora) en que el turno todavía no empezó. */
    static LocalDate proximaFecha(LocalDate desde, LocalTime inicio, LocalDateTime ahora) {
        LocalDate fecha = desde;
        while (!fecha.atTime(inicio).isAfter(ahora)) {
            fecha = fecha.plusWeeks(1);
        }
        return fecha;
    }

    /** Los jugadores se actualizan en su lugar, posición por posición. */
    private static void asignarJugadores(TurnoFijo tf, List<DatosJugador> jugadores) {
        for (int i = 0; i < jugadores.size(); i++) {
            int posicion = i + 1;
            DatosJugador datos = jugadores.get(i);
            TurnoFijoJugador jugador = tf.getJugadores().stream()
                    .filter(j -> j.getPosicion() == posicion)
                    .findFirst()
                    .orElseGet(() -> {
                        TurnoFijoJugador nuevo = TurnoFijoJugador.builder().turnoFijo(tf).posicion(posicion).build();
                        tf.getJugadores().add(nuevo);
                        return nuevo;
                    });
            jugador.setUsuario(datos.usuario());
            jugador.setNombre(datos.nombre());
            jugador.setTelefono(datos.telefono());
        }
    }

    private TurnoFijo buscar(Long id) {
        return turnoFijoRepository.findById(id)
                .orElseThrow(() -> new TurnoFijoNoEncontradoException(id));
    }

    private TurnoFijo bloquearActivo(Long id) {
        TurnoFijo tf = turnoFijoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new TurnoFijoNoEncontradoException(id));
        if (!Boolean.TRUE.equals(tf.getActivo())) {
            throw new EstadoInvalidoException("El turno fijo #" + id + " está dado de baja.");
        }
        return tf;
    }

    private Usuario admin(String email) {
        Usuario usuario = usuarioService.autenticado(email);
        Roles.exigirAdmin(usuario, "Solo el ADMIN puede manejar los turnos fijos.");
        return usuario;
    }

    private static int cantidad(Integer cantidad) {
        return cantidad != null ? cantidad : 0;
    }

    static String dia(DayOfWeek dia) {
        return Fechas.nombreDia(dia);
    }

    TurnoFijoResponse aResponse(TurnoFijo tf) {
        TurnoFijoResponse response = new TurnoFijoResponse();
        response.setId(tf.getId());
        response.setCanchaId(tf.getCancha().getId());
        response.setCanchaNumero(tf.getCancha().getNumero());
        response.setDiaSemana(tf.getDiaSemana());
        response.setHoraInicio(tf.getHoraInicio());
        response.setHoraFin(tf.getHoraFin());
        response.setDesde(tf.getDesde());
        response.setCantidadPelotas(tf.getCantidadPelotas());
        response.setCantidadPaletas(tf.getCantidadPaletas());
        response.setActivo(tf.getActivo());
        response.setCreatedAt(tf.getCreatedAt());
        response.setDadoDeBajaAt(tf.getDadoDeBajaAt());
        response.setJugadores(tf.getJugadores().stream()
                .map(j -> new TurnoFijoResponse.Jugador(j.getPosicion(),
                        j.getUsuario() != null ? j.getUsuario().getId() : null, j.nombreParaMostrar(),
                        j.getUsuario() != null ? j.getUsuario().getTelefono() : j.getTelefono(), j.getUsuario() == null))
                .toList());
        LocalDateTime ahora = reloj.ahora();
        response.setProximosPartidos(reservaRepository.findIdsFuturosDelTurnoFijo(tf.getId(), ahora.toLocalDate(),
                ahora.toLocalTime()).stream()
                .map(reservaRepository::findById)
                .flatMap(java.util.Optional::stream)
                .map(r -> new TurnoFijoResponse.Partido(r.getId(), r.getFechaReserva(), r.getEstado(),
                        r.getMontoTotal(), r.estaInactiva() ? java.math.BigDecimal.ZERO : r.getMontoPendiente()))
                .toList());
        return response;
    }
}
