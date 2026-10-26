package com.LosCiruelos.padel_club_api.reservas.dto.response;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;

import lombok.Data;

@Data
public class TurnoFijoResponse {
    Long id;
    Long canchaId;
    Integer canchaNumero;
    DayOfWeek diaSemana;
    LocalTime horaInicio;
    LocalTime horaFin;
    // primera fecha que se juega
    LocalDate desde;
    Integer cantidadPelotas;
    Integer cantidadPaletas;
    Boolean activo;
    LocalDateTime createdAt;
    LocalDateTime dadoDeBajaAt;
    List<Jugador> jugadores;
    // partidos ya creados cuyo turno todavía no empezó (también las fechas dadas de baja, CANCELADA)
    List<Partido> proximosPartidos;
    // solo al crear o modificar: fechas dentro de la anticipación en las que no se pudo crear el
    // partido (horario ocupado, el club no abre, falta stock); el job lo vuelve a intentar
    List<FechaOmitida> fechasOmitidas;
    // solo al modificar: partidos futuros con pagos, que quedaron como estaban (se editan uno por uno)
    List<Long> partidosSinCambiar;

    public record Jugador(Integer posicion, Long usuarioId, String nombre, String telefono, Boolean invitado) {
    }

    public record Partido(Long reservaId, LocalDate fecha, EstadoReserva estado, BigDecimal montoTotal,
            BigDecimal montoPendiente) {
    }

    public record FechaOmitida(LocalDate fecha, String motivo) {
    }
}
