package com.LosCiruelos.padel_club_api.Services.Avisos;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.Entities.AvisosAdmin;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.Repository.AvisosAdminRepository;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.Services.Avisos.AvisoReserva.Destinatario;
import com.LosCiruelos.padel_club_api.Services.Email.EmailServiceFactory;
import com.LosCiruelos.padel_club_api.Services.WhatsApp.WhatsAppServiceFactory;

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
    private final AvisosAdminRepository avisosAdminRepository;
    private final WhatsAppServiceFactory whatsApp;
    private final EmailServiceFactory emailServiceFactory;

    @Transactional(readOnly = true)
    public void enviar(AvisoReserva aviso) {
        if (!aviso.tipo().esParaAdmin()) {
            entregar(aviso.cliente(), aviso);
            return;
        }
        List<Usuario> admins = usuarioService.adminsActivos();
        Map<Long, AvisosAdmin> preferencias = avisosAdminRepository
                .findAllById(admins.stream().map(Usuario::getId).toList()).stream()
                .collect(Collectors.toMap(AvisosAdmin::getUsuarioId, Function.identity()));
        for (Usuario admin : admins) {
            AvisosAdmin prefs = preferencias.getOrDefault(admin.getId(), AvisosAdmin.porDefecto(admin.getId()));
            if (quiere(prefs, aviso.tipo())) {
                entregar(Destinatario.de(admin), aviso);
            }
        }
    }

    /** Canal que se usa de verdad para el destinatario. */
    public CanalNotificacion canalPara(Destinatario destinatario) {
        boolean puedeWhatsapp = destinatario.canal() == CanalNotificacion.WHATSAPP
                && destinatario.telefonoVerificado() && destinatario.telefono() != null
                && whatsApp.getService().estaConfigurado();
        return puedeWhatsapp ? CanalNotificacion.WHATSAPP : CanalNotificacion.EMAIL;
    }

    /** Envía el aviso a un destinatario. @return el canal por el que salió, o null si falló. */
    public CanalNotificacion entregar(Destinatario destinatario, AvisoReserva aviso) {
        try {
            if (canalPara(destinatario) == CanalNotificacion.WHATSAPP && whatsApp.getService()
                    .enviarPlantilla(destinatario.telefono(), aviso.tipo().plantilla(), aviso.parametros())) {
                return CanalNotificacion.WHATSAPP;
            }
            emailServiceFactory.getService().sendAviso(destinatario.email(), aviso.tipo().asunto(), aviso.texto());
            log.info("Aviso {} de la reserva #{} enviado por mail a {}", aviso.tipo(), aviso.reservaId(),
                    destinatario.email());
            return CanalNotificacion.EMAIL;
        } catch (Exception e) {
            log.error("No se pudo enviar el aviso {} de la reserva #{} a {}: {}", aviso.tipo(), aviso.reservaId(),
                    destinatario.email(), e.getMessage());
            return null;
        }
    }

    static boolean quiere(AvisosAdmin prefs, TipoAviso tipo) {
        if (!Boolean.TRUE.equals(prefs.getActivos())) {
            return false;
        }
        return switch (tipo) {
            case RESERVA_CONFIRMADA -> prefs.getReservaConfirmada();
            case SALDO_COBRADO -> prefs.getSaldoCobrado();
            // el resultado de un reembolso pendiente es parte de la cancelación
            case RESERVA_CANCELADA, REEMBOLSO_REALIZADO -> prefs.getReservaCancelada();
            case REEMBOLSO_MANUAL -> prefs.getReembolsoManual();
            default -> false;
        };
    }
}
