package com.LosCiruelos.padel_club_api.club.dto.response;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Lo que queda afuera de un horario de apertura nuevo: reservas futuras que siguen en pie y turnos
 * fijos activos. Va en el 409 de PUT /admin/configuracion.
 */
public record FueraDeHorarioResponse(List<Reserva> reservasFueraDeHorario, List<TurnoFijo> turnosFijosFueraDeHorario) {

    /** @param tipo NORMAL o PARTIDO */
    public record Reserva(Long id, LocalDate fecha, LocalTime horaInicio, LocalTime horaFin, Integer canchaNumero,
            String clienteNombre, String tipo) {
    }

    public record TurnoFijo(Long id, DayOfWeek diaSemana, LocalTime horaInicio, LocalTime horaFin,
            Integer canchaNumero) {
    }
}
