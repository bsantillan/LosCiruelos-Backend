package com.LosCiruelos.padel_club_api.club.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.club.dto.request.CierreRequest;
import com.LosCiruelos.padel_club_api.club.dto.request.ConfiguracionRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.CancelacionesResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.CierreResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.ConfiguracionResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.HorariosClubResponse;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.mapper.ConfiguracionMapper;

import lombok.RequiredArgsConstructor;

/**
 * Toda la configuración del club junta: la consulta y los cambios del ADMIN (incluidos los días
 * cerrados), y los horarios públicos. Si un cambio deja afuera reservas y el ADMIN confirma, primero
 * se guarda (en su transacción) y después se cancela cada reserva con reembolso total, en su propia
 * transacción: una que no se pueda cancelar no revierte el cambio ni las demás.
 */
@Service
@RequiredArgsConstructor
public class ConfiguracionGeneralService {

    private final ConfiguracionRepository configuracionRepository;
    private final ConfiguracionService configuracionService;
    private final ConfiguracionMapper configuracionMapper;
    private final CambiosDeConfiguracion cambios;
    private final HorariosOcupados horariosOcupados;
    private final Reloj reloj;

    public ConfiguracionResponse general() {
        return configuracionMapper.aResponse(configuracionRepository.obtener());
    }

    /** Días y horarios de apertura, próximos días cerrados y duración de los turnos (público; de la caché). */
    public HorariosClubResponse horarios() {
        return configuracionMapper.aHorarios(configuracionService.obtener(), reloj.hoy());
    }

    /**
     * Reemplaza toda la configuración. Vale para lo que se reserve o se cobre desde ahora: las
     * reservas que ya existen conservan su precio y su seña. Si el horario nuevo deja afuera reservas
     * o turnos fijos que entraban en el actual, sin {@code confirmar} responde 409 con la lista y no
     * guarda nada; con {@code confirmar}, guarda, cancela esas reservas con reembolso total (con aviso
     * al cliente) y los turnos fijos afuera no generan partidos nuevos.
     */
    public ConfiguracionResponse actualizar(ConfiguracionRequest req, String email) {
        CambiosDeConfiguracion.Guardado<ConfiguracionResponse> guardado = cambios.guardar(req);
        return configuracionMapper.conCancelaciones(guardado.respuesta(), cancelar(guardado.aCancelar(), email));
    }

    /** Los días cerrados de hoy en adelante. */
    public List<CierreResponse> cierres() {
        return configuracionRepository.obtener().cierresDesde(reloj.hoy()).stream()
                .map(c -> configuracionMapper.aCierre(c, null))
                .toList();
    }

    /**
     * Cierra el club un día puntual: ese día no se puede reservar y los turnos fijos no crean su
     * partido. Si hay reservas ese día que todavía no empezaron, sin {@code confirmar} responde 409
     * con la lista; con {@code confirmar}, cierra y las cancela con reembolso total.
     */
    public CierreResponse cerrar(CierreRequest req, String email) {
        CambiosDeConfiguracion.Guardado<CierreResponse> guardado = cambios.cerrar(req, email);
        CierreResponse cierre = guardado.respuesta();
        return new CierreResponse(cierre.fecha(), cierre.motivo(), cierre.cerradoAt(),
                cancelar(guardado.aCancelar(), email));
    }

    public void reabrir(LocalDate fecha) {
        cambios.reabrir(fecha);
    }

    private CancelacionesResponse cancelar(List<Long> reservaIds, String email) {
        return reservaIds.isEmpty() ? CancelacionesResponse.NINGUNA : horariosOcupados.cancelar(reservaIds, email);
    }
}
