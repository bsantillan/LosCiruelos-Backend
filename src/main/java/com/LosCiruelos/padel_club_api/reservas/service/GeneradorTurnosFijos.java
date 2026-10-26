package com.LosCiruelos.padel_club_api.reservas.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.reservas.dto.response.TurnoFijoResponse.FechaOmitida;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijo;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnoFijoRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Crea los partidos de los turnos fijos con {@code turnos-fijos.semanas-anticipacion} semanas de
 * anticipación (4 por defecto). Cada fecha se crea en su propia transacción
 * ({@link PartidoService#crearParaTurnoFijo}): si una no se puede (horario ocupado, el club no
 * abre, falta stock) se informa y se sigue con las demás; el job lo vuelve a intentar.
 */
@Slf4j
@Component
public class GeneradorTurnosFijos {

    private final TurnoFijoRepository turnoFijoRepository;
    private final PartidoService partidoService;
    private final Reloj reloj;
    private final int semanasAnticipacion;

    public GeneradorTurnosFijos(TurnoFijoRepository turnoFijoRepository, PartidoService partidoService, Reloj reloj,
            @Value("${turnos-fijos.semanas-anticipacion:4}") int semanasAnticipacion) {
        this.turnoFijoRepository = turnoFijoRepository;
        this.partidoService = partidoService;
        this.reloj = reloj;
        this.semanasAnticipacion = semanasAnticipacion;
    }

    /** Partidos creados y fechas que no se pudieron crear. */
    public record Resultado(List<Long> creados, List<FechaOmitida> omitidas) {
    }

    /** Crea los partidos que falten del turno fijo, desde hoy hasta la anticipación configurada. */
    public Resultado generar(Long turnoFijoId) {
        List<Long> creados = new ArrayList<>();
        List<FechaOmitida> omitidas = new ArrayList<>();
        TurnoFijo tf = turnoFijoRepository.findById(turnoFijoId).orElse(null);
        if (tf == null || !Boolean.TRUE.equals(tf.getActivo())) {
            return new Resultado(creados, omitidas);
        }

        LocalDateTime ahora = reloj.ahora();
        LocalDate hasta = ahora.toLocalDate().plusWeeks(semanasAnticipacion);
        for (LocalDate fecha = TurnoFijoService.proximaFecha(tf.getDesde(), tf.getHoraInicio(), ahora);
                !fecha.isAfter(hasta); fecha = fecha.plusWeeks(1)) {
            try {
                partidoService.crearParaTurnoFijo(turnoFijoId, fecha).ifPresent(creados::add);
            } catch (RuntimeException e) {
                omitidas.add(new FechaOmitida(fecha, e.getMessage()));
            }
        }
        if (!omitidas.isEmpty()) {
            log.info("Turno fijo #{}: no se pudieron crear los partidos de {}", turnoFijoId, omitidas);
        }
        return new Resultado(creados, omitidas);
    }

    /** Todos los turnos fijos activos (lo llama el job). @return cantidad de partidos creados */
    public int generarTodos() {
        int creados = 0;
        for (Long id : turnoFijoRepository.findIdsActivos()) {
            try {
                creados += generar(id).creados().size();
            } catch (Exception e) {
                log.error("No se pudieron crear los partidos del turno fijo #{}", id, e);
            }
        }
        return creados;
    }
}
