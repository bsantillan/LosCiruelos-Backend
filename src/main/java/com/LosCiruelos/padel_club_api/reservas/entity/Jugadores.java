package com.LosCiruelos.padel_club_api.reservas.entity;

import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;

/** Utilidades compartidas por los jugadores de un partido y de un turno fijo. */
public final class Jugadores {

    /** Un partido tiene de 1 a 4 jugadores: el 1 (responsable) es obligatorio; del 2 al 4, opcionales. */
    public static final int MAXIMO_POR_PARTIDO = 4;

    private Jugadores() {
    }

    static String nombreDe(Usuario usuario, String nombreInvitado) {
        if (usuario == null) {
            return nombreInvitado;
        }
        String nombre = usuario.getNombre() != null ? usuario.getNombre() : "";
        String apellido = usuario.getApellido() != null ? usuario.getApellido() : "";
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? usuario.getEmail() : completo;
    }
}
