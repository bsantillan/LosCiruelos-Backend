package com.LosCiruelos.padel_club_api.common;

import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException;

/** Chequeos de rol dentro de los services (las rutas ya se protegen con @PreAuthorize). */
public final class Roles {

    private Roles() {
    }

    /** ADMIN o EMPLEADO. */
    public static boolean esPersonal(Usuario usuario) {
        return usuario.getRol() == Role.ADMIN || usuario.getRol() == Role.EMPLEADO;
    }

    public static boolean esAdmin(Usuario usuario) {
        return usuario.getRol() == Role.ADMIN;
    }

    /** @throws PermisoDenegadoException (403) con {@code mensaje} si no es ADMIN */
    public static void exigirAdmin(Usuario usuario, String mensaje) {
        if (!esAdmin(usuario)) {
            throw new PermisoDenegadoException(mensaje);
        }
    }
}
