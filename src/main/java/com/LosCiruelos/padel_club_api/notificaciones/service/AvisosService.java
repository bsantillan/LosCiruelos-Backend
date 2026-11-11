package com.LosCiruelos.padel_club_api.notificaciones.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.notificaciones.aviso.AvisoReserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.notificaciones.aviso.AvisoReserva.Destinatario;
import com.LosCiruelos.padel_club_api.notificaciones.email.EmailServiceFactory;
import com.LosCiruelos.padel_club_api.notificaciones.whatsapp.WhatsAppServiceFactory;

import org.springframework.beans.factory.annotation.Value;

import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Entrega los avisos por el canal que eligió cada destinatario: WhatsApp si lo eligió, tiene el
 * teléfono verificado y WhatsApp está configurado; si no, mail. Nunca por los dos: solo si
 * WhatsApp falla se manda el mail como respaldo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvisosService {

    private final UsuarioService usuarioService;
    private final WhatsAppServiceFactory whatsApp;
    private final EmailServiceFactory emailServiceFactory;

    // los links de los avisos al cliente (pagar la seña) van a la pantalla del front
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Transactional(readOnly = true)
    public void enviar(AvisoReserva aviso) {
        if (aviso.tipo() == TipoAviso.SENIA_PENDIENTE) {
            aviso = aviso.conLink(linkDePago(aviso.reservaId()));
        }
        if (!aviso.tipo().esParaAdmin()) {
            entregar(aviso.cliente(), aviso);
            return;
        }
        for (Usuario admin : usuarioService.adminsActivos()) {
            if (admin.avisosAdmin().quiere(aviso.tipo())) {
                entregar(Destinatario.de(admin), aviso);
            }
        }
    }

    /** La pantalla de pago del cliente para esa reserva (la misma que usa al reservar). */
    String linkDePago(Long reservaId) {
        String base = frontendUrl != null && frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
        return base + "/reservas/" + reservaId + "/pago";
    }

    /** Canal que se usa de verdad para el destinatario. */
    public CanalNotificacion canalPara(Destinatario destinatario) {
        boolean puedeWhatsapp = destinatario.canal() == CanalNotificacion.WHATSAPP
                && destinatario.telefonoVerificado() && destinatario.telefono() != null
                && whatsApp.getService().estaConfigurado();
        return puedeWhatsapp ? CanalNotificacion.WHATSAPP : CanalNotificacion.EMAIL;
    }

    private boolean porWhatsApp(Destinatario destinatario, AvisoReserva aviso) {
        String telefono = destinatario.telefono();
        String plantilla = aviso.tipo().plantilla();
        String boton = aviso.parametroBoton();
        return boton == null
                ? whatsApp.getService().enviarPlantilla(telefono, plantilla, aviso.parametros())
                : whatsApp.getService().enviarPlantilla(telefono, plantilla, aviso.parametros(), boton);
    }

    /** Envía el aviso a un destinatario. @return el canal por el que salió, o null si falló. */
    public CanalNotificacion entregar(Destinatario destinatario, AvisoReserva aviso) {
        try {
            if (canalPara(destinatario) == CanalNotificacion.WHATSAPP && porWhatsApp(destinatario, aviso)) {
                return CanalNotificacion.WHATSAPP;
            }
            emailServiceFactory.getService().sendAviso(destinatario.email(), aviso.tipo().asunto(), aviso.textoMail());
            log.info("Aviso {} de la reserva #{} enviado por mail a {}", aviso.tipo(), aviso.reservaId(),
                    destinatario.email());
            return CanalNotificacion.EMAIL;
        } catch (Exception e) {
            log.error("No se pudo enviar el aviso {} de la reserva #{} a {}: {}", aviso.tipo(), aviso.reservaId(),
                    destinatario.email(), e.getMessage());
            return null;
        }
    }
}
