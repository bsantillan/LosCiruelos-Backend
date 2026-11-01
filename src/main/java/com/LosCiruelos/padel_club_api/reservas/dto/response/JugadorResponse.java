package com.LosCiruelos.padel_club_api.reservas.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Jugador de un partido. El partido se paga como un todo: cómo se dividen el monto es cosa de ellos. */
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
}
