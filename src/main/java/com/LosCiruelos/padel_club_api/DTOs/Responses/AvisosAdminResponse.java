package com.LosCiruelos.padel_club_api.DTOs.Responses;

import com.LosCiruelos.padel_club_api.Entities.AvisosAdmin;

/** @param activos interruptor general: en false no le llega ningún aviso */
public record AvisosAdminResponse(boolean activos, boolean reservaConfirmada, boolean saldoCobrado,
        boolean reservaCancelada, boolean reembolsoManual) {

    public static AvisosAdminResponse from(AvisosAdmin avisos) {
        return new AvisosAdminResponse(avisos.getActivos(), avisos.getReservaConfirmada(), avisos.getSaldoCobrado(),
                avisos.getReservaCancelada(), avisos.getReembolsoManual());
    }
}
