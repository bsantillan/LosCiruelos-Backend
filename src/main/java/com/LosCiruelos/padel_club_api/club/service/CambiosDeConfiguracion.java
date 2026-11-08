package com.LosCiruelos.padel_club_api.club.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.club.dto.request.CierreRequest;
import com.LosCiruelos.padel_club_api.club.dto.request.ConfiguracionRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.CierreResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.ConfiguracionResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.entity.DiaCerrado;
import com.LosCiruelos.padel_club_api.club.exception.HorariosConReservasException;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.config.AppConfig.ConfiguracionCache;
import com.LosCiruelos.padel_club_api.mapper.ConfiguracionMapper;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Los cambios de la configuración, cada uno en su transacción (y con la caché invalidada). Si el
 * cambio deja reservas afuera y no se confirmó, 409 con la lista y no se guarda nada; si se confirmó,
 * devuelve las reservas a cancelar, que {@link ConfiguracionGeneralService} cancela después del
 * commit, una por una.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CambiosDeConfiguracion {

    /** Lo guardado y las reservas que hay que cancelar ahora (vacío si no hay o no se confirmó). */
    public record Guardado<T>(T respuesta, List<Long> aCancelar) {
    }

    private final ConfiguracionRepository configuracionRepository;
    private final ConfiguracionMapper configuracionMapper;
    private final HorariosOcupados horariosOcupados;
    private final UsuarioService usuarioService;
    private final Reloj reloj;

    /** Reemplaza toda la configuración (ver {@link ConfiguracionGeneralService#actualizar}). */
    @Transactional
    @CacheEvict(value = ConfiguracionCache.NOMBRE, allEntries = true)
    public Guardado<ConfiguracionResponse> guardar(ConfiguracionRequest req) {
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
        boolean hayAlgo = !nuevos.reservasFueraDeHorario().isEmpty() || !nuevos.turnosFijosFueraDeHorario().isEmpty();
        if (hayAlgo && !Boolean.TRUE.equals(req.getConfirmar())) {
            // la transacción se revierte: no se guarda nada
            throw HorariosConReservasException.porHorario(nuevos);
        }
        if (hayAlgo) {
            log.warn("Configuración guardada con {} reservas y {} turnos fijos fuera del horario nuevo: se cancelan las "
                    + "reservas", nuevos.reservasFueraDeHorario().size(), nuevos.turnosFijosFueraDeHorario().size());
        }
        return new Guardado<>(configuracionMapper.aResponse(config), ids(nuevos));
    }

    /** Cierra el club un día puntual (ver {@link ConfiguracionGeneralService#cerrar}). */
    @Transactional
    @CacheEvict(value = ConfiguracionCache.NOMBRE, allEntries = true)
    public Guardado<CierreResponse> cerrar(CierreRequest req, String email) {
        Long adminId = usuarioService.autenticado(email).getId();
        Configuracion config = configuracionRepository.obtener();
        DiaCerrado cierre = config.cerrar(req.getFecha(), req.getMotivo().trim(), adminId, reloj.ahora());

        FueraDeHorarioResponse delDia = horariosOcupados.delDia(req.getFecha());
        if (!delDia.reservasFueraDeHorario().isEmpty() && !Boolean.TRUE.equals(req.getConfirmar())) {
            throw HorariosConReservasException.porCierre(req.getFecha(), delDia);
        }
        log.info("Club cerrado el {} ({}) por {}: {} reservas a cancelar", req.getFecha(), cierre.getMotivo(), email,
                delDia.reservasFueraDeHorario().size());
        return new Guardado<>(configuracionMapper.aCierre(cierre, null), ids(delDia));
    }

    /** Vuelve a abrir un día cerrado (las reservas que se cancelaron no vuelven). */
    @Transactional
    @CacheEvict(value = ConfiguracionCache.NOMBRE, allEntries = true)
    public void reabrir(LocalDate fecha) {
        configuracionRepository.obtener().reabrir(fecha);
        log.info("El club vuelve a abrir el {}", fecha);
    }

    private static List<Long> ids(FueraDeHorarioResponse fuera) {
        return fuera.reservasFueraDeHorario().stream().map(FueraDeHorarioResponse.Reserva::id).toList();
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
