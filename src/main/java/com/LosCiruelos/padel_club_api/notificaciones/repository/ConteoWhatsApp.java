package com.LosCiruelos.padel_club_api.notificaciones.repository;

import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;
import com.LosCiruelos.padel_club_api.notificaciones.entity.enums.MotivoWhatsApp;

/** Cuántos WhatsApp se mandaron de un motivo, aviso (null en los códigos) y destinatario. */
public record ConteoWhatsApp(MotivoWhatsApp motivo, TipoAviso aviso, boolean paraCliente, long cantidad) {
}
