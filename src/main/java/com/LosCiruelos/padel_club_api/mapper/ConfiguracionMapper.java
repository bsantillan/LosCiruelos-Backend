package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.club.dto.response.ConfiguracionResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.RecordatorioConfigResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.ReservasConfigResponse;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
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
                c.getMinutosRecordatorio(), c.getDiasAnticipacionReserva(),
                c.diasEnOrden().stream()
                        .map(d -> new ConfiguracionResponse.Horario(d.getDia(), d.getHorario_inicio(), d.getHorario_fin()))
                        .toList());
    }

    public ReservasConfigResponse aReservas(Configuracion config) {
        return new ReservasConfigResponse(config.getDiasAnticipacionReserva());
    }
}
