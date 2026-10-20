package com.LosCiruelos.padel_club_api.Config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class AppConfigTest {

    @Test
    void elRelojDeLaAppTienePrecisionDeMilisegundos() {
        // en Windows el reloj del sistema tiene 100 ns y las fechas salían con 7 decimales
        Clock reloj = new AppConfig().clock();
        for (int i = 0; i < 50; i++) {
            assertThat(LocalDateTime.now(reloj).getNano() % 1_000_000).isZero();
        }
    }
}
