package com.LosCiruelos.padel_club_api.notificaciones.entity.enums;

/** Por qué se mandó un WhatsApp: Meta cobra distinto el código (autenticación) y los avisos (utilidad). */
public enum MotivoWhatsApp {
    /** El código para verificar el celular. */
    CODIGO,
    /** Un aviso de una reserva ({@code TipoAviso}). */
    AVISO
}
