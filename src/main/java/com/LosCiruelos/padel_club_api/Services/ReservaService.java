package com.LosCiruelos.padel_club_api.Services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.DTOs.Requests.CrearReservaRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ReservaResponse;
import com.LosCiruelos.padel_club_api.Entities.Cancha;
import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.Entities.DiaApertura;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.PerfilIncompletoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.ReservaRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservaService {

    private static final List<EstadoReserva> ESTADOS_INACTIVOS = List.of(
            EstadoReserva.CANCELADA,
            EstadoReserva.EXPIRADA);

    private final ConfiguracionService configuracionService;
    private final UsuarioService usuarioService;
    private final ClienteProfileService clienteProfileService;
    private final ReservaRepository reservaRepository;
    private final CanchaService canchaService;

    public Reserva findById(Long id) {
        return reservaRepository.findById(id).orElse(null);
    }

    public Reserva findByIdOrThrow(Long id, RuntimeException ex) {
        return reservaRepository.findById(id).orElseThrow(() -> ex);
    }

    public Reserva save(Reserva reserva) {
        return reservaRepository.save(reserva);
    }

    public List<Reserva> findByEstadoAndExpiresAtBefore(EstadoReserva estado, LocalDateTime fechaHora) {
        return reservaRepository.findByEstadoAndExpiresAtBefore(estado, fechaHora);
    }

    public List<Reserva> findByAll(Usuario cliente) {
        return reservaRepository.findAllByCliente(cliente);
    }

    public Long countPartidosEsteMes(Long cliente_id, EstadoReserva estado) {
        LocalDate hoy = LocalDate.now();

        return reservaRepository.countPartidosEsteMes(
                cliente_id,
                estado,
                hoy.withDayOfMonth(1),
                hoy.withDayOfMonth(hoy.lengthOfMonth()));
    }

    public Optional<Reserva> ultimaReservaCompletada(Usuario cliente, EstadoReserva estado) {
        return reservaRepository.findFirstByClienteAndEstadoOrderByFechaReservaDescHoraInicioDesc(cliente, estado);
    }

    @Transactional
    public ReservaResponse crearReserva(CrearReservaRequest req, String email_solicitante) {

        Usuario solicitante = usuarioService.findByEmail(email_solicitante);
        Usuario cliente = resolverCliente(req.getClienteId(), solicitante);

        ClienteProfile perfilCliente = clienteProfileService.findByUsuario(cliente);
        if (!clienteProfileService.esPerfilCompleto(perfilCliente)) {
            boolean esCliente = solicitante.getRol() == Role.CLIENTE;
            String mensaje = esCliente
                    ? "Debés completar tu perfil antes de reservar."
                    : "El cliente %s %s debe completar su perfil antes de reservar."
                            .formatted(cliente.getNombre(), cliente.getApellido());
            throw new PerfilIncompletoException(mensaje);
        }

        Configuracion config = configuracionService.obtener();
        Cancha cancha = canchaService.findByIdOrThrow(req.getCanchaId());

        if (req.getFechaReserva().isEqual(LocalDate.now())) {
            LocalTime ahora = LocalTime.now();
            if (req.getHoraInicio().isBefore(ahora)) {
                throw new ReservaException("No podés reservar un horario que ya pasó.");
            }
        }

        if (!req.getHoraInicio().isBefore(req.getHoraFin())) {
            throw new ReservaException("La hora de inicio debe ser anterior a la hora de fin.");
        }

        long duracionMinutos = Duration.between(req.getHoraInicio(), req.getHoraFin()).toMinutes();

        if (duracionMinutos % 30 != 0) {
            throw new ReservaException("El turno debe durar un múltiplo de 30 minutos.");
        }

        if (duracionMinutos < config.getDuracionMinimaTurno()) {
            throw new ReservaException(
                    "La duración mínima del turno es de %d minutos.".formatted(config.getDuracionMinimaTurno()));
        }
        if (duracionMinutos > config.getDuracionMaximaTurno()) {
            throw new ReservaException(
                    "La duración máxima del turno es de %d minutos.".formatted(config.getDuracionMaximaTurno()));
        }

        DiaApertura diaApertura = obtenerDiaApertura(config, req.getFechaReserva());
        if (req.getHoraInicio().isBefore(diaApertura.getHorario_inicio())) {
            throw new ReservaException(
                    "El horario de apertura el %s es a las %s."
                            .formatted(diaApertura.getDia(), diaApertura.getHorario_inicio()));
        }
        if (req.getHoraFin().isAfter(diaApertura.getHorario_fin())) {
            throw new ReservaException(
                    "El horario de cierre el %s es a las %s."
                            .formatted(diaApertura.getDia(), diaApertura.getHorario_fin()));
        }

        long minutosDesdeApertura = Duration.between(diaApertura.getHorario_inicio(), req.getHoraInicio()).toMinutes();
        if (minutosDesdeApertura % 30 != 0) {
            throw new ReservaException(
                    "El horario de inicio debe coincidir con un slot válido (cada 30 minutos desde la apertura).");
        }

        Optional<Reserva> reservaExistente = reservaRepository
                .findPendienteVigenteByClienteAndCanchaAndFechaAndHoraInicio(
                        cliente.getId(), cancha.getId(),
                        req.getFechaReserva(), req.getHoraInicio(), LocalDateTime.now());

        if (reservaExistente.isPresent()) {
            Reserva reserva = reservaExistente.get();

            if (!reserva.getHoraFin().equals(req.getHoraFin())) {
                long nuevaDuracion = Duration.between(
                        req.getHoraInicio(), req.getHoraFin()).toMinutes();
                reserva.setHoraFin(req.getHoraFin());
                reserva.setMontoTotal(calcularMonto(nuevaDuracion, config));
                reserva.setPelotas(req.getPelotas());
                reserva.setPaletas(req.getPaletas());
                reservaRepository.save(reserva);
                log.info("Reserva #{} actualizada con nuevo horaFin", reserva.getId());
            }
            return ReservaResponse.from(reserva);
        }

        List<Reserva> pendientesSinPago = reservaRepository
                .findPendientesSinPagoByClienteAndCanchaAndFecha(
                        cliente.getId(), cancha.getId(),
                        req.getFechaReserva(), LocalDateTime.now());

        pendientesSinPago.forEach(r -> {
            r.setEstado(EstadoReserva.CANCELADA);
            reservaRepository.save(r);
            log.info("Reserva #{} cancelada automáticamente — cliente eligió nuevo horario", r.getId());
        });

        List<Reserva> solapadas = reservaRepository.findSolapadas(
                cancha.getId(), req.getFechaReserva(),
                req.getHoraInicio(), req.getHoraFin(),
                ESTADOS_INACTIVOS);
        if (!solapadas.isEmpty()) {
            throw new ReservaException(
                    "El horario solicitado se superpone con un turno ya reservado en esa cancha.");
        }

        BigDecimal montoTotal = calcularMonto(duracionMinutos, config);

        Reserva reserva = Reserva.builder()
                .cancha(cancha)
                .cliente(cliente)
                .createdBy(solicitante)
                .fechaReserva(req.getFechaReserva())
                .horaInicio(req.getHoraInicio())
                .horaFin(req.getHoraFin())
                .montoTotal(montoTotal)
                .pelotas(req.getPelotas())
                .paletas(req.getPaletas())
                .estado(EstadoReserva.PENDIENTE)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .build();

        reserva = reservaRepository.save(reserva);
        log.info("Reserva #{} creada para cliente {} en cancha {} el {}",
                reserva.getId(), cliente.getId(), cancha.getNumero(), req.getFechaReserva());

        return ReservaResponse.from(reserva);
    }

    @Transactional
    public void cancelarReserva(Long id, String email) {
        Reserva reserva = findByIdOrThrow(id, new ReservaException("Reserva no encontrada"));

        if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
            throw new ReservaException(
                    "Solo se pueden cancelar reservas PENDIENTE. Estado actual: "
                            + reserva.getEstado());
        }

        reserva.setEstado(EstadoReserva.CANCELADA);
        reservaRepository.save(reserva);

        log.info("Reserva #{} cancelada por usuario {}", id, email);
    }

    private DiaApertura obtenerDiaApertura(Configuracion config, LocalDate fecha) {
        String nombreDia = fecha.getDayOfWeek()
                .getDisplayName(TextStyle.FULL, new Locale("es", "AR"));
        // Capitalizar primera letra para que coincida con "Lunes", "Martes", etc.
        nombreDia = nombreDia.substring(0, 1).toUpperCase() + nombreDia.substring(1);

        log.info("Día calculado: '{}'", nombreDia); // ✅ agregás esto
        log.info("Días en config: {}", config.getDias_apertura().stream()
                .map(DiaApertura::getDia).toList()); // ✅ y esto

        final String diaFinal = nombreDia;
        return config.getDias_apertura().stream()
                .filter(d -> d.getDia().equalsIgnoreCase(diaFinal))
                .findFirst()
                .orElseThrow(() -> new ReservaException(
                        "El local no tiene horario configurado para el día: " + diaFinal));
    }

    private BigDecimal calcularMonto(long duracionMinutos, Configuracion config) {
        long bloques = duracionMinutos / 30;

        BigDecimal montoReserva = BigDecimal.valueOf(config.getMontoReserva());
        BigDecimal montoBloques = BigDecimal.valueOf(config.getMontoXMediHora())
                .multiply(BigDecimal.valueOf(bloques));

        return montoReserva.add(montoBloques)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private Usuario resolverCliente(Long clienteId, Usuario solicitante) {
        return switch (solicitante.getRol()) {
            case CLIENTE -> {
                if (clienteId != null) {
                    throw new ReservaException("Un cliente solo puede reservar para sí mismo.");
                }
                yield solicitante;
            }
            case ADMIN, EMPLEADO -> {
                if (clienteId == null) {
                    throw new ReservaException(
                            "ADMIN y EMPLEADO deben especificar un clienteId al crear una reserva.");
                }
                yield usuarioService.findByIdOrThrow(clienteId);
            }
            case PROFESOR -> throw new ReservaException(
                    "Los profesores no pueden crear reservas de canchas.");
        };
    }
}
