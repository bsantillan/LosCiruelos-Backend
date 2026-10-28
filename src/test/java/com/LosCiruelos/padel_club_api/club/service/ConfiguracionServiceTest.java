package com.LosCiruelos.padel_club_api.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.dto.request.RecordatorioConfigRequest;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.mapper.ConfiguracionMapper;

class ConfiguracionServiceTest {

    private ConfiguracionRepository configuracionRepository;
    private ConfiguracionService configuracionService;

    @BeforeEach
    void setUp() {
        configuracionRepository = mock(ConfiguracionRepository.class);
        when(configuracionRepository.obtener()).thenReturn(TestData.configuracion());
        when(configuracionRepository.save(any(Configuracion.class))).thenAnswer(inv -> inv.getArgument(0));
        configuracionService = new ConfiguracionService(configuracionRepository, new ConfiguracionMapper());
    }

    @Test
    void configuraElRecordatorio() {
        RecordatorioConfigRequest req = new RecordatorioConfigRequest();
        req.setRecordatorioActivo(false);
        req.setMinutosRecordatorio(60);

        var r = configuracionService.actualizarRecordatorio(req);

        assertThat(r.recordatorioActivo()).isFalse();
        assertThat(r.minutosRecordatorio()).isEqualTo(60);
        ArgumentCaptor<Configuracion> guardada = ArgumentCaptor.forClass(Configuracion.class);
        verify(configuracionRepository).save(guardada.capture());
        assertThat(guardada.getValue().getMinutosRecordatorio()).isEqualTo(60);
    }

    @Test
    void devuelveElRecordatorioConfigurado() {
        var r = configuracionService.recordatorio();

        assertThat(r.recordatorioActivo()).isEqualTo(TestData.configuracion().getRecordatorioActivo());
        assertThat(r.minutosRecordatorio()).isEqualTo(TestData.configuracion().getMinutosRecordatorio());
    }
}
