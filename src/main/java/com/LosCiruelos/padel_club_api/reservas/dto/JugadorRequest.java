package com.LosCiruelos.padel_club_api.reservas.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Jugador de un partido o turno fijo: un cliente registrado (usuarioId) o un invitado (nombre y,
 * opcionalmente, teléfono). Va uno u otro.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JugadorRequest {

    private Long usuarioId;

    @Size(min = 2, max = 100, message = "El nombre del invitado tiene que tener entre 2 y 100 caracteres")
    private String nombre;

    @Pattern(regexp = "^\\+?[0-9 ()-]{6,20}$", message = "El teléfono del invitado no es válido")
    private String telefono;

    public static JugadorRequest registrado(Long usuarioId) {
        return new JugadorRequest(usuarioId, null, null);
    }

    public static JugadorRequest invitado(String nombre) {
        return new JugadorRequest(null, nombre, null);
    }
}
