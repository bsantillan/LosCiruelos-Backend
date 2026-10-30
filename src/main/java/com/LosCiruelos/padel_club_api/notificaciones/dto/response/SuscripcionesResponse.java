package com.LosCiruelos.padel_club_api.notificaciones.dto.response;

/**
 * @param cancelaciones siempre true: las cancelaciones de sus reservas se avisan siempre
 * @param editable      false mientras el perfil no esté completo (no puede cambiar sus avisos)
 */
public record SuscripcionesResponse(boolean cancelaciones, boolean recordatorios, boolean promociones,
        boolean editable) {
}
