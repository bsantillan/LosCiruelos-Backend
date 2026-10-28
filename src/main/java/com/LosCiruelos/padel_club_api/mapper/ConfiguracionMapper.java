package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.club.dto.response.RecordatorioConfigResponse;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;

/** Configuración del club → lo que edita el ADMIN. */
@Component
public class ConfiguracionMapper {

    public RecordatorioConfigResponse aRecordatorio(Configuracion config) {
        return new RecordatorioConfigResponse(config.getRecordatorioActivo(), config.getMinutosRecordatorio());
    }
}
