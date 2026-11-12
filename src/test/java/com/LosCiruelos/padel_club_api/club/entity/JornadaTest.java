package com.LosCiruelos.padel_club_api.club.entity;

import static com.LosCiruelos.padel_club_api.TestData.HOY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.common.Jornada;

/** La jornada del día D va de la apertura de D a la apertura de D+1 (hoy es el martes 13/10/2026). */
class JornadaTest {

    private final Configuracion config = TestData.configuracion(); // lunes a viernes 8 a 23, sábado y domingo 9

    @Test
    void conElClubAbiertoEsLaJornadaDeHoyHastaLaAperturaDeManiana() {
        Jornada jornada = config.jornadaEn(HOY.atTime(10, 0));

        assertThat(jornada.fecha()).isEqualTo(HOY);
        assertThat(jornada.inicio()).isEqualTo(HOY.atTime(8, 0));
        assertThat(jornada.fin()).isEqualTo(HOY.plusDays(1).atTime(8, 0));
    }

    @Test
    void pasadaLaMedianocheSigueLaJornadaDelDiaAnterior() {
        Jornada jornada = config.jornadaEn(HOY.plusDays(1).atTime(0, 30));

        assertThat(jornada.fecha()).isEqualTo(HOY);
        assertThat(jornada.incluye(HOY.atTime(22, 0))).isTrue();
        assertThat(jornada.incluye(HOY.plusDays(1).atTime(0, 30))).isTrue();
        // a la apertura empieza la jornada nueva
        assertThat(config.jornadaEn(HOY.plusDays(1).atTime(8, 0)).fecha()).isEqualTo(HOY.plusDays(1));
        assertThat(jornada.incluye(HOY.plusDays(1).atTime(8, 0))).isFalse();
    }

    @Test
    void elViernesTerminaALaAperturaDelSabado() {
        LocalDate viernes = LocalDate.of(2026, 10, 16);
        Jornada jornada = config.jornadaEn(viernes.plusDays(1).atTime(8, 30));

        assertThat(jornada.fecha()).isEqualTo(viernes);
        assertThat(jornada.fin()).isEqualTo(viernes.plusDays(1).atTime(9, 0));
    }

    @Test
    void unDiaSinHorarioUsaLaAperturaMasTempranaDeLaSemana() {
        config.cambiarHorarios(List.of(
                new Configuracion.Horario("Martes", LocalTime.of(10, 0), LocalTime.of(23, 0)),
                new Configuracion.Horario("Jueves", LocalTime.of(9, 0), LocalTime.of(22, 0))));

        // el martes cierra la jornada a la apertura más temprana (9:00), porque el miércoles no abre
        Jornada martes = config.jornadaEn(HOY.atTime(11, 0));
        assertThat(martes.fin()).isEqualTo(HOY.plusDays(1).atTime(9, 0));
        assertThat(config.jornadaEn(HOY.plusDays(1).atTime(8, 0)).fecha()).isEqualTo(HOY);
    }

    @Test
    void losDiasCerradosNoCambianLaJornada() {
        config.cerrar(HOY.plusDays(1), "Mantenimiento", 1L, HOY.atTime(10, 0));

        assertThat(config.jornadaEn(HOY.plusDays(1).atTime(12, 0)).fecha()).isEqualTo(HOY.plusDays(1));
        assertThat(config.jornadaEn(HOY.plusDays(1).atTime(7, 0)).fecha()).isEqualTo(HOY);
    }
}
