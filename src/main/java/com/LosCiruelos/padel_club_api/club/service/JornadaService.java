package com.LosCiruelos.padel_club_api.club.service;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.common.Jornada;
import com.LosCiruelos.padel_club_api.common.Reloj;

import lombok.RequiredArgsConstructor;

/**
 * La jornada de trabajo en curso ({@link com.LosCiruelos.padel_club_api.club.entity.Configuracion#jornadaEn}):
 * es el "hoy" del EMPLEADO, que pasada la medianoche sigue viendo y operando la jornada que todavía no terminó.
 */
@Service
@RequiredArgsConstructor
public class JornadaService {

    private final ConfiguracionService configuracionService;
    private final Reloj reloj;

    public Jornada actual() {
        return configuracionService.obtener().jornadaEn(reloj.ahora());
    }
}
