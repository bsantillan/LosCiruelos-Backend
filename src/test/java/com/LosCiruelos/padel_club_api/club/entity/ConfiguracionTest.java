package com.LosCiruelos.padel_club_api.club.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.exception.DiaSinAperturaException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

/** Reglas de la configuración del club (con la de TestData: turnos de 90 a 180 minutos). */
class ConfiguracionTest {

    private final Configuracion config = TestData.configuracion();

    @Test
    void montoDelTurnoEsBaseMasBloquesDe30Minutos() {
        assertThat(config.montoTurno(90)).isEqualByComparingTo("15000");
        config.setMontoReserva(new BigDecimal("1000"));
        assertThat(config.montoTurno(120)).isEqualByComparingTo("21000");
    }

    @Test
    void seniaSeCalculaConDecimalesExactos() {
        assertThat(config.senia(new BigDecimal("15000"))).isEqualByComparingTo("7500");
        config.setPorcentajeSenia(new BigDecimal("30"));
        // con float daba 4500.0001788...
        assertThat(config.senia(new BigDecimal("15000"))).isEqualByComparingTo("4500.00");
        config.setPorcentajeSenia(new BigDecimal("33.33"));
        assertThat(config.senia(new BigDecimal("10000"))).isEqualByComparingTo("3333.00");
    }

    @Test
    void diaDeAperturaPorDiaDeLaSemana() {
        assertThat(config.diaApertura(LocalDate.of(2026, 10, 14)).getDia()).isEqualTo("Miércoles");
        assertThat(config.diaApertura(LocalDate.of(2026, 10, 17)).getDia()).isEqualTo("Sábado");

        config.getDias_apertura().removeIf(d -> d.getDia().equals("Domingo"));
        assertThatThrownBy(() -> config.diaApertura(LocalDate.of(2026, 10, 18)))
                .isInstanceOf(DiaSinAperturaException.class);
    }

    @Test
    void laDuracionEsEnBloquesDe30DentroDelMinimoYElMaximo() {
        assertThatCode(() -> config.validarDuracion(90)).doesNotThrowAnyException();
        assertThatCode(() -> config.validarDuracion(180)).doesNotThrowAnyException();
        assertThatThrownBy(() -> config.validarDuracion(100))
                .isInstanceOf(ReservaException.class).hasMessage("El turno debe durar un múltiplo de 30 minutos.");
        assertThatThrownBy(() -> config.validarDuracion(60))
                .isInstanceOf(ReservaException.class).hasMessage("La duración mínima del turno es de 90 minutos.");
        assertThatThrownBy(() -> config.validarDuracion(210))
                .isInstanceOf(ReservaException.class).hasMessage("La duración máxima del turno es de 180 minutos.");
    }
}
