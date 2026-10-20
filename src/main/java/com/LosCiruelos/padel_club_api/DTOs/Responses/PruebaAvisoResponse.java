package com.LosCiruelos.padel_club_api.DTOs.Responses;

import com.LosCiruelos.padel_club_api.Entities.Enum.CanalNotificacion;

/** @param canal por dónde salió el aviso de prueba (null si no se pudo enviar) */
public record PruebaAvisoResponse(boolean enviado, CanalNotificacion canal, String destino) {
}
