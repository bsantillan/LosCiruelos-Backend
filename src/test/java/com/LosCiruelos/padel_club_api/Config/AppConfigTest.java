package com.LosCiruelos.padel_club_api.Config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.common.Reloj;

class AppConfigTest {

    @Test
    void elRelojDeLaAppTienePrecisionDeMilisegundos() {
        // en Windows el reloj del sistema tiene 100 ns y las fechas salían con 7 decimales
        Reloj reloj = new AppConfig().reloj();
        for (int i = 0; i < 50; i++) {
            assertThat(reloj.ahora().getNano() % 1_000_000).isZero();
        }
    }
}
