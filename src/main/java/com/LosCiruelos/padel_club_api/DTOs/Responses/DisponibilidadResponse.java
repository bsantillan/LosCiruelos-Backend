package com.LosCiruelos.padel_club_api.DTOs.Responses;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DisponibilidadResponse {
    private LocalTime horarioApertura;
    private LocalTime horarioCierre;
    private Integer duracionMinimaTurno;
    private Integer duracionMaximaTurno;
    // duración (minutos) usada para calcular turnosDisponibles
    private Integer duracion;
    // precio de la cancha para esa duración (sin productos): el mismo que se cobra al reservar
    private BigDecimal montoCancha;
    // primer inicio posible: la apertura, o el próximo turno si la fecha es hoy (null si ya no quedan)
    private LocalTime horarioMinimoInicio;
    // turnos tomados por otras reservas
    private List<Turno> slotsOcupados;
    // turnos de la duración pedida que se pueden reservar
    private List<Turno> turnosDisponibles;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Turno {
        private LocalTime horaInicio;
        private LocalTime horaFin;
    }
}
