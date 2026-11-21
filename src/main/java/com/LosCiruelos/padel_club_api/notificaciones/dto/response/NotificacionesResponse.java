package com.LosCiruelos.padel_club_api.notificaciones.dto.response;

import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;

/**
 * @param canal              el que eligió el usuario
 * @param canalEnUso         el que se usa de verdad (EMAIL si eligió WhatsApp pero falta verificar el teléfono)
 * @param whatsappDisponible si al usuario le puede llegar WhatsApp: el club lo tiene configurado y, si
 *                           es CLIENTE, el ADMIN permite mandarle WhatsApp a los clientes
 */
public record NotificacionesResponse(CanalNotificacion canal, CanalNotificacion canalEnUso, String telefono,
        boolean telefonoVerificado, boolean whatsappDisponible) {
}
