package com.LosCiruelos.padel_club_api.DTOs;

import java.time.LocalTime;

/** Un alquiler de producto en un horario (para calcular cuántas unidades están en uso). */
public record UsoAlquiler(LocalTime horaInicio, LocalTime horaFin, Integer cantidad) {
}
