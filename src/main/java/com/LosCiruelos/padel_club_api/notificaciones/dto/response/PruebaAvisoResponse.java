package com.LosCiruelos.padel_club_api.notificaciones.dto.response;

import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;

/** @param canal por dónde salió el aviso de prueba (null si no se pudo enviar) */
public record PruebaAvisoResponse(boolean enviado, CanalNotificacion canal, String destino) {
}
