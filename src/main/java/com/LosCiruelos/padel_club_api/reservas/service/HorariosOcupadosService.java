package com.LosCiruelos.padel_club_api.reservas.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.club.dto.response.CancelacionesResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.service.HorariosOcupados;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.mapper.FueraDeHorarioMapper;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.OpcionReembolso;
import com.LosCiruelos.padel_club_api.reservas.repository.ReservaRepository;
import com.LosCiruelos.padel_club_api.reservas.repository.TurnoFijoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Las reservas y turnos fijos que no entran en los horarios de una configuración (la que el ADMIN
 * está por guardar) o que caen en un día que se cierra, y su cancelación. Se fija con la regla de
 * {@link Configuracion#motivoFueraDeHorario}. Sin @Transactional: cada cancelación abre la suya.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HorariosOcupadosService implements HorariosOcupados {

    private final ReservaRepository reservaRepository;
    private final TurnoFijoRepository turnoFijoRepository;
    private final FueraDeHorarioMapper mapper;
    private final CancelacionService cancelacionService;
    private final Reloj reloj;

    @Override
    public FueraDeHorarioResponse fueraDeHorario(Configuracion config) {
        LocalDateTime ahora = reloj.ahora();
        return new FueraDeHorarioResponse(
                futurasQueSiguen(ahora).stream()
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

    @Override
    public FueraDeHorarioResponse delDia(LocalDate fecha) {
        return new FueraDeHorarioResponse(
                futurasQueSiguen(reloj.ahora()).stream()
                        .filter(r -> r.getFechaReserva().equals(fecha))
                        .map(mapper::aReserva)
                        .toList(),
                turnoFijoRepository.findByActivoOrderByDiaSemanaAscHoraInicioAsc(true).stream()
                        .filter(tf -> tf.seJuegaEl(fecha))
                        .map(mapper::aTurnoFijo)
                        .toList());
    }

    @Override
    public CancelacionesResponse cancelar(List<Long> reservaIds, String emailAdmin) {
        List<Long> canceladas = new ArrayList<>();
        List<CancelacionesResponse.SinCancelar> sinCancelar = new ArrayList<>();
        for (Long id : reservaIds) {
            try {
                cancelacionService.cancelarComoPersonal(id, OpcionReembolso.TOTAL, emailAdmin);
                canceladas.add(id);
            } catch (RuntimeException e) {
                log.warn("No se pudo cancelar la reserva #{} fuera del horario del club: {}", id, e.getMessage());
                sinCancelar.add(new CancelacionesResponse.SinCancelar(id, e.getMessage()));
            }
        }
        log.info("Reservas fuera del horario del club: {} canceladas, {} sin cancelar", canceladas.size(),
                sinCancelar.size());
        return new CancelacionesResponse(canceladas, sinCancelar);
    }

    private List<Reserva> futurasQueSiguen(LocalDateTime ahora) {
        return reservaRepository.findFuturasQueSiguen(ahora.toLocalDate(), ahora.toLocalTime(),
                List.of(EstadoReserva.CANCELADA, EstadoReserva.EXPIRADA));
    }
}
