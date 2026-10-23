package com.LosCiruelos.padel_club_api.Services.WhatsApp;

import java.util.List;

/**
 * Envío de mensajes de WhatsApp con plantillas aprobadas. Hoy se implementa con la API oficial
 * de Meta ({@link MetaWhatsAppService}); otro proveedor (Twilio, 360dialog, …) sería otra
 * implementación elegida con {@code whatsapp.provider}.
 *
 * Los envíos nunca lanzan excepción: un error del proveedor se informa con {@code false}.
 */
public interface WhatsAppService {

    /** Si tiene las credenciales para enviar. Sin ellas, los avisos van por mail. */
    boolean estaConfigurado();

    /** Plantilla con parámetros de texto en el cuerpo ({{1}}, {{2}}, …). */
    boolean enviarPlantilla(String telefono, String plantilla, List<String> parametros);

    /** Plantilla de autenticación con el código de verificación. */
    boolean enviarCodigo(String telefono, String plantilla, String codigo);
}
