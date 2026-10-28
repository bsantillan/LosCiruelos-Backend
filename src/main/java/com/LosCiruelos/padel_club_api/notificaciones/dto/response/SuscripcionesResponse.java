package com.LosCiruelos.padel_club_api.notificaciones.dto.response;

import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;

/**
 * @param cancelaciones siempre true: las cancelaciones de sus reservas se avisan siempre
 * @param editable      false mientras el perfil no esté completo (no puede cambiar sus avisos)
 */
public record SuscripcionesResponse(boolean cancelaciones, boolean recordatorios, boolean promociones,
        boolean editable) {

    public static SuscripcionesResponse from(Usuario usuario, boolean editable) {
        return new SuscripcionesResponse(true, Boolean.TRUE.equals(usuario.getRecibeRecordatorios()),
                Boolean.TRUE.equals(usuario.getRecibePromociones()), editable);
    }
}
