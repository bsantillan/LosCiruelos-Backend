package com.LosCiruelos.padel_club_api.notificaciones.dto.response;

import java.time.LocalDate;
import java.util.List;

import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;

/**
 * Los WhatsApp que mandó el club entre desde y hasta (inclusive). Cada uno lo cobra Meta.
 *
 * @param codigos   códigos para verificar el celular (Meta los cobra como "autenticación")
 * @param avisos    avisos de reservas (Meta los cobra como "utilidad")
 * @param aClientes a clientes (códigos y avisos)
 * @param alPersonal al ADMIN, EMPLEADO o PROFESOR
 * @param porAviso  los avisos por tipo, del más mandado al menos (sin los que no se mandaron)
 */
public record MensajesWhatsAppResponse(LocalDate desde, LocalDate hasta, long total, long codigos, long avisos,
        long aClientes, long alPersonal, List<PorAviso> porAviso) {

    public record PorAviso(TipoAviso aviso, String nombre, long cantidad) {
    }
}
