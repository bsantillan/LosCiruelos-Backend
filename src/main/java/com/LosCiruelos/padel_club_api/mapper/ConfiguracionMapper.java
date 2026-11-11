package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.club.dto.response.CancelacionesResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.CierreResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.ConfiguracionResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.HorariosClubResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.RecordatorioConfigResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.ReservasConfigResponse;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.entity.DiaCerrado;
import com.LosCiruelos.padel_club_api.common.Dinero;

/** Configuración del club → lo que edita el ADMIN. */
@Component
public class ConfiguracionMapper {

    public RecordatorioConfigResponse aRecordatorio(Configuracion config) {
        return new RecordatorioConfigResponse(config.getRecordatorioActivo(), config.getMinutosRecordatorio());
    }

    public ConfiguracionResponse aResponse(Configuracion c) {
        return new ConfiguracionResponse(Dinero.redondear(c.getMontoReserva()), Dinero.redondear(c.getMontoXMediaHora()),
                Dinero.redondear(c.getPorcentajeSenia()), c.getDuracionMinimaTurno(), c.getDuracionMaximaTurno(),
                c.getMinutosReembolsoTotal(), c.getMinutosReembolsoSenia(), c.getRecordatorioActivo(),
                c.getMinutosRecordatorio(), c.getDiasAnticipacionReserva(), c.getMinutosPagoSeniaOnline(), horarios(c),
                null);
    }

    /** La configuración guardada con las reservas que se cancelaron al confirmar. */
    public ConfiguracionResponse conCancelaciones(ConfiguracionResponse r, CancelacionesResponse cancelaciones) {
        return new ConfiguracionResponse(r.montoReserva(), r.montoXMediaHora(), r.porcentajeSenia(),
                r.duracionMinimaTurno(), r.duracionMaximaTurno(), r.minutosReembolsoTotal(), r.minutosReembolsoSenia(),
                r.recordatorioActivo(), r.minutosRecordatorio(), r.diasAnticipacion(), r.minutosPagoSeniaOnline(),
                r.horarios(), cancelaciones);
    }

    /** @param hoy los días cerrados que van son de hoy en adelante */
    public HorariosClubResponse aHorarios(Configuracion c, java.time.LocalDate hoy) {
        return new HorariosClubResponse(horarios(c),
                c.cierresDesde(hoy).stream().map(d -> new HorariosClubResponse.Cierre(d.getFecha(), d.getMotivo()))
                        .toList(),
                c.getDuracionMinimaTurno(), c.getDuracionMaximaTurno());
    }

    public CierreResponse aCierre(DiaCerrado cierre, CancelacionesResponse cancelaciones) {
        return new CierreResponse(cierre.getFecha(), cierre.getMotivo(), cierre.getCreatedAt(), cancelaciones);
    }

    private static java.util.List<ConfiguracionResponse.Horario> horarios(Configuracion c) {
        return c.diasEnOrden().stream()
                .map(d -> new ConfiguracionResponse.Horario(d.getDia(), d.getHorario_inicio(), d.getHorario_fin()))
                .toList();
    }

    public ReservasConfigResponse aReservas(Configuracion config) {
        return new ReservasConfigResponse(config.getDiasAnticipacionReserva());
    }
}
