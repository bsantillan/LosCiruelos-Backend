package com.LosCiruelos.padel_club_api.notificaciones.dto.response;

/** @param activos interruptor general: en false no le llega ningún aviso */
public record AvisosAdminResponse(boolean activos, boolean reservaConfirmada, boolean saldoCobrado,
        boolean reservaCancelada, boolean reembolsoManual) {
}
