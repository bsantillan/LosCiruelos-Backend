package com.LosCiruelos.padel_club_api.DTOs.Responses;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Jugador de un partido y cuánto puso. parte es el total dividido entre los 4; debe es lo que le
 * falta de su parte (0 si la reserva ya está cubierta, aunque haya pagado otro).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JugadorResponse {
    Long id;
    Integer posicion;
    // null: invitado
    Long usuarioId;
    String nombre;
    // solo para el personal (null para los jugadores)
    String telefono;
    Boolean invitado;
    BigDecimal parte;
    // plata que pagó (sin bonificaciones, descontado lo devuelto)
    BigDecimal pagado;
    BigDecimal bonificado;
    BigDecimal debe;
}
