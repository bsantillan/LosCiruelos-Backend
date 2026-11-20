package com.LosCiruelos.padel_club_api.club.dto.response;

/**
 * @param whatsappClientes si el club manda WhatsApp a los clientes (apagado: avisos por mail y el
 *                         celular del cliente no se verifica)
 */
public record RecordatorioConfigResponse(boolean recordatorioActivo, int minutosRecordatorio,
        boolean whatsappClientes) {
}
