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

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.DTOs.Requests.CrearReservaRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ReservaResponse;
import com.LosCiruelos.padel_club_api.Entities.Cancha;
import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.Entities.DiaApertura;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
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
    private final ReservaRepository reservaRepository;
    private final CanchaService canchaService;

    @Transactional
    public ReservaResponse crearReserva(CrearReservaRequest req, String email_solicitante) {

        Usuario solicitante = usuarioService.findByEmail(email_solicitante);
        Usuario cliente = resolverCliente(req.getClienteId(), solicitante);

        Configuracion config = configuracionService.obtener();
        Cancha cancha = canchaService.obtenerCancha(req.getCanchaId());

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
                .build();

        reserva = reservaRepository.save(reserva);
        log.info("Reserva #{} creada para cliente {} en cancha {} el {}",
                reserva.getId(), cliente.getId(), cancha.getNumero(), req.getFechaReserva());

        return ReservaResponse.from(reserva);
    }

    private DiaApertura obtenerDiaApertura(Configuracion config, LocalDate fecha) {
        String nombreDia = fecha.getDayOfWeek()
                .getDisplayName(TextStyle.FULL, new Locale("es", "AR"));
        // Capitalizar primera letra para que coincida con "Lunes", "Martes", etc.
        nombreDia = nombreDia.substring(0, 1).toUpperCase() + nombreDia.substring(1);

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
