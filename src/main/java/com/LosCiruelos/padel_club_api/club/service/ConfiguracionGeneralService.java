package com.LosCiruelos.padel_club_api.club.service;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.club.dto.request.ConfiguracionRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.ConfiguracionResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.HorariosClubResponse;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.exception.HorariosConReservasException;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.config.AppConfig.ConfiguracionCache;
import com.LosCiruelos.padel_club_api.mapper.ConfiguracionMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Toda la configuración del club junta: la consulta y el cambio del ADMIN, y los horarios públicos.
 * Antes de cambiar los horarios se revisa qué reservas y turnos fijos quedan afuera.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfiguracionGeneralService {

    private final ConfiguracionRepository configuracionRepository;
    private final ConfiguracionService configuracionService;
    private final ConfiguracionMapper configuracionMapper;
    private final HorariosOcupados horariosOcupados;

    public ConfiguracionResponse general() {
        return configuracionMapper.aResponse(configuracionRepository.obtener());
    }

    /** Días y horarios de apertura y duración de los turnos (público; de la configuración en caché). */
    public HorariosClubResponse horarios() {
        return configuracionMapper.aHorarios(configuracionService.obtener());
    }

    /**
     * Reemplaza toda la configuración. Vale para lo que se reserve o se cobre desde ahora: las
     * reservas que ya existen conservan su precio y su seña. Si el horario nuevo deja afuera reservas
     * o turnos fijos que entraban en el actual, sin {@code confirmar} responde 409 con la lista y no
     * guarda nada; con {@code confirmar}, guarda igual y no las toca (el ADMIN las reprograma o
     * cancela a mano; los turnos fijos no generan partidos en un horario que ya no existe).
     */
    @Transactional
    @CacheEvict(value = ConfiguracionCache.NOMBRE, allEntries = true)
    public ConfiguracionResponse actualizar(ConfiguracionRequest req) {
        Configuracion config = configuracionRepository.obtener();
        // lo que ya estaba afuera del horario actual no se vuelve a avisar
        FueraDeHorarioResponse antes = horariosOcupados.fueraDeHorario(config);

        config.setMontoReserva(Dinero.redondear(req.getMontoReserva()));
        config.setMontoXMediaHora(Dinero.redondear(req.getMontoXMediaHora()));
        config.setPorcentajeSenia(Dinero.redondear(req.getPorcentajeSenia()));
        config.cambiarDuracionDeTurnos(req.getDuracionMinimaTurno(), req.getDuracionMaximaTurno());
        config.cambiarPoliticaCancelacion(req.getMinutosReembolsoTotal(), req.getMinutosReembolsoSenia());
        config.setRecordatorioActivo(req.getRecordatorioActivo());
        config.setMinutosRecordatorio(req.getMinutosRecordatorio());
        config.setDiasAnticipacionReserva(req.getDiasAnticipacion());
        config.cambiarHorarios(req.getHorarios().stream()
                .map(h -> new Configuracion.Horario(h.getDia(), h.getApertura(), h.getCierre()))
                .toList());

        FueraDeHorarioResponse nuevos = nuevos(antes, horariosOcupados.fueraDeHorario(config));
        if (!nuevos.reservasFueraDeHorario().isEmpty() || !nuevos.turnosFijosFueraDeHorario().isEmpty()) {
            if (!Boolean.TRUE.equals(req.getConfirmar())) {
                // la transacción se revierte: no se guarda nada
                throw new HorariosConReservasException(nuevos);
            }
            log.warn("Configuración guardada con {} reservas y {} turnos fijos fuera del horario nuevo: {}",
                    nuevos.reservasFueraDeHorario().size(), nuevos.turnosFijosFueraDeHorario().size(), nuevos);
        }
        return configuracionMapper.aResponse(config); // se guarda al terminar la transacción
    }

    /** Lo que queda afuera ahora y no estaba afuera antes. */
    private static FueraDeHorarioResponse nuevos(FueraDeHorarioResponse antes, FueraDeHorarioResponse despues) {
        Set<Long> reservas = antes.reservasFueraDeHorario().stream().map(FueraDeHorarioResponse.Reserva::id)
                .collect(Collectors.toSet());
        Set<Long> turnos = antes.turnosFijosFueraDeHorario().stream().map(FueraDeHorarioResponse.TurnoFijo::id)
                .collect(Collectors.toSet());
        return new FueraDeHorarioResponse(
                despues.reservasFueraDeHorario().stream().filter(r -> !reservas.contains(r.id())).toList(),
                despues.turnosFijosFueraDeHorario().stream().filter(t -> !turnos.contains(t.id())).toList());
    }
}
