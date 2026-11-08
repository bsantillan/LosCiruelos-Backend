package com.LosCiruelos.padel_club_api.reservas.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.service.HorariosOcupados;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.mapper.FueraDeHorarioMapper;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnoFijoRepository;

import lombok.RequiredArgsConstructor;

/**
 * Las reservas y turnos fijos que no entran en los horarios de una configuración (la que el ADMIN
 * está por guardar). Se fija con la regla de {@link Configuracion#motivoFueraDeHorario}.
 */
@Service
@RequiredArgsConstructor
public class HorariosOcupadosService implements HorariosOcupados {

    private final ReservaRepository reservaRepository;
    private final TurnoFijoRepository turnoFijoRepository;
    private final FueraDeHorarioMapper mapper;
    private final Reloj reloj;

    @Override
    public FueraDeHorarioResponse fueraDeHorario(Configuracion config) {
        LocalDateTime ahora = reloj.ahora();
        return new FueraDeHorarioResponse(
                reservaRepository.findFuturasQueSiguen(ahora.toLocalDate(), ahora.toLocalTime(),
                        List.of(EstadoReserva.CANCELADA, EstadoReserva.EXPIRADA)).stream()
                        .filter(r -> config.motivoFueraDeHorario(r.getFechaReserva().getDayOfWeek(), r.getHoraInicio(),
                                r.getHoraFin()) != null)
                        .map(mapper::aReserva)
                        .toList(),
                turnoFijoRepository.findByActivoOrderByDiaSemanaAscHoraInicioAsc(true).stream()
                        .filter(tf -> config.motivoFueraDeHorario(tf.getDiaSemana(), tf.getHoraInicio(),
                                tf.getHoraFin()) != null)
                        .map(mapper::aTurnoFijo)
                        .toList());
    }
}
