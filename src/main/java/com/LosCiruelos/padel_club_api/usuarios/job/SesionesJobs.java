package com.LosCiruelos.padel_club_api.usuarios.job;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.usuarios.service.RefreshTokenService;

import lombok.RequiredArgsConstructor;

/** Tareas programadas de las sesiones de los usuarios. */
@Component
@RequiredArgsConstructor
public class SesionesJobs {

    private final RefreshTokenService refreshTokenService;

    /** Todas las noches a las 2 borra las sesiones (refresh tokens) vencidas. */
    @Scheduled(cron = "0 0 2 * * ?")
    public void borrarSesionesVencidas() {
        refreshTokenService.borrarVencidos();
    }
}
