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

    /** La jornada del día {@code fecha} (la caja de ese día va de su apertura a la del día siguiente). */
    public Jornada del(java.time.LocalDate fecha) {
        return configuracionService.obtener().jornadaDel(fecha);
    }
}
