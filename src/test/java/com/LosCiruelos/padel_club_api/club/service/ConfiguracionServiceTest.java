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
import com.LosCiruelos.padel_club_api.club.dto.request.ConfiguracionRequest;
import com.LosCiruelos.padel_club_api.club.dto.request.RecordatorioConfigRequest;
import com.LosCiruelos.padel_club_api.club.dto.request.ReservasConfigRequest;
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

    @Test
    void configuraLaAnticipacionParaReservar() {
        Configuracion config = TestData.configuracion();
        when(configuracionRepository.obtener()).thenReturn(config);
        assertThat(configuracionService.reservas().diasAnticipacion()).isEqualTo(30); // por defecto
        ReservasConfigRequest req = new ReservasConfigRequest();
        req.setDiasAnticipacion(45);

        var r = configuracionService.actualizarReservas(req);

        assertThat(r.diasAnticipacion()).isEqualTo(45);
        assertThat(config.getDiasAnticipacionReserva()).isEqualTo(45); // se guarda al terminar la transacción
    }

    @Test
    void devuelveTodaLaConfiguracionConLosDiasDeLunesADomingo() {
        var r = configuracionService.general();

        assertThat(r.montoXMediaHora()).isEqualTo(new java.math.BigDecimal("5000.00"));
        assertThat(r.porcentajeSenia()).isEqualTo(new java.math.BigDecimal("50.00"));
        assertThat(r.duracionMinimaTurno()).isEqualTo(90);
        assertThat(r.minutosReembolsoTotal()).isEqualTo(1440);
        assertThat(r.diasAnticipacion()).isEqualTo(30);
        assertThat(r.horarios()).extracting(h -> h.dia()).containsExactly("Lunes", "Martes", "Miércoles", "Jueves",
                "Viernes", "Sábado", "Domingo");
        assertThat(r.horarios().get(5).apertura()).isEqualTo(java.time.LocalTime.of(9, 0));
    }

    private static ConfiguracionRequest pedido() {
        ConfiguracionRequest req = new ConfiguracionRequest();
        req.setMontoReserva(new java.math.BigDecimal("1000"));
        req.setMontoXMediaHora(new java.math.BigDecimal("6000.5"));
        req.setPorcentajeSenia(new java.math.BigDecimal("30"));
        req.setDuracionMinimaTurno(60);
        req.setDuracionMaximaTurno(120);
        req.setMinutosReembolsoTotal(2880);
        req.setMinutosReembolsoSenia(720);
        req.setRecordatorioActivo(false);
        req.setMinutosRecordatorio(60);
        req.setDiasAnticipacion(15);
        ConfiguracionRequest.Horario sabado = new ConfiguracionRequest.Horario();
        sabado.setDia("sabado");
        sabado.setApertura(java.time.LocalTime.of(10, 0));
        sabado.setCierre(java.time.LocalTime.of(21, 0));
        ConfiguracionRequest.Horario lunes = new ConfiguracionRequest.Horario();
        lunes.setDia("Lunes");
        lunes.setApertura(java.time.LocalTime.of(8, 0));
        lunes.setCierre(java.time.LocalTime.of(23, 0));
        req.setHorarios(java.util.List.of(sabado, lunes));
        return req;
    }

    @Test
    void reemplazaTodaLaConfiguracion() {
        Configuracion config = TestData.configuracion();
        when(configuracionRepository.obtener()).thenReturn(config);

        var r = configuracionService.actualizarGeneral(pedido());

        assertThat(config.getMontoReserva()).isEqualTo(new java.math.BigDecimal("1000.00"));
        assertThat(config.getMontoXMediaHora()).isEqualTo(new java.math.BigDecimal("6000.50"));
        assertThat(config.getPorcentajeSenia()).isEqualTo(new java.math.BigDecimal("30.00"));
        assertThat(config.getDuracionMinimaTurno()).isEqualTo(60);
        assertThat(config.getDuracionMaximaTurno()).isEqualTo(120);
        assertThat(config.getMinutosReembolsoTotal()).isEqualTo(2880);
        assertThat(config.getMinutosReembolsoSenia()).isEqualTo(720);
        assertThat(config.getRecordatorioActivo()).isFalse();
        assertThat(config.getMinutosRecordatorio()).isEqualTo(60);
        assertThat(config.getDiasAnticipacionReserva()).isEqualTo(15);
        assertThat(r.horarios()).extracting(h -> h.dia()).containsExactly("Lunes", "Sábado");
        // el precio de un turno de 90 minutos con la configuración nueva
        assertThat(config.montoTurno(90)).isEqualTo(new java.math.BigDecimal("19001.50"));
    }

    @Test
    void lasReglasDeLaConfiguracionSeValidan() {
        ConfiguracionRequest req = pedido();
        req.setMinutosReembolsoTotal(60);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> configuracionService.actualizarGeneral(req))
                .isInstanceOf(com.LosCiruelos.padel_club_api.common.exception.ReservaException.class)
                .hasMessage("La anticipación para devolver todo no puede ser menor que la de devolver la seña.");
    }
}
