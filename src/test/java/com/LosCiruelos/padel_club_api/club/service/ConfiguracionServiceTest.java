package com.LosCiruelos.padel_club_api.club.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.Exceptions.DiaSinAperturaException;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;

class ConfiguracionServiceTest {

    private final ConfiguracionService servicio = new ConfiguracionService(mock(ConfiguracionRepository.class));
    private final Configuracion config = TestData.configuracion();

    @Test
    void montoDelTurnoEsBaseMasBloquesDe30Minutos() {
        assertThat(servicio.calcularMontoTurno(config, 90)).isEqualByComparingTo("15000");
        config.setMontoReserva(new BigDecimal("1000"));
        assertThat(servicio.calcularMontoTurno(config, 120)).isEqualByComparingTo("21000");
    }

    @Test
    void seniaSeCalculaConDecimalesExactos() {
        assertThat(servicio.calcularSenia(config, new BigDecimal("15000"))).isEqualByComparingTo("7500");
        config.setPorcentajeSenia(new BigDecimal("30"));
        // con float daba 4500.0001788...
        assertThat(servicio.calcularSenia(config, new BigDecimal("15000"))).isEqualByComparingTo("4500.00");
        config.setPorcentajeSenia(new BigDecimal("33.33"));
        assertThat(servicio.calcularSenia(config, new BigDecimal("10000"))).isEqualByComparingTo("3333.00");
    }

    @Test
    void diaDeAperturaPorDiaDeLaSemana() {
        assertThat(servicio.obtenerDiaApertura(config, LocalDate.of(2026, 10, 14)).getDia()).isEqualTo("Miércoles");
        assertThat(servicio.obtenerDiaApertura(config, LocalDate.of(2026, 10, 17)).getDia()).isEqualTo("Sábado");

        config.getDias_apertura().removeIf(d -> d.getDia().equals("Domingo"));
        assertThatThrownBy(() -> servicio.obtenerDiaApertura(config, LocalDate.of(2026, 10, 18)))
                .isInstanceOf(DiaSinAperturaException.class);
    }
}
