package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.notificaciones.dto.response.AvisosAdminResponse;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.NotificacionesResponse;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.SuscripcionesResponse;
import com.LosCiruelos.padel_club_api.usuarios.entity.AdminProfile;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;

/** Preferencias de aviso del usuario (canal y suscripciones) y de cada ADMIN → sus respuestas. */
@Component
public class NotificacionesMapper {

    /**
     * @param canalEnUso         el que se usa de verdad (lo resuelve {@code AvisosService.canalPara})
     * @param whatsappDisponible si el club tiene WhatsApp configurado
     */
    public NotificacionesResponse aResponse(Usuario usuario, CanalNotificacion canalEnUso,
            boolean whatsappDisponible) {
        return new NotificacionesResponse(usuario.getCanalNotificaciones(), canalEnUso, usuario.getTelefono(),
                Boolean.TRUE.equals(usuario.getTelefonoVerificado()), whatsappDisponible);
    }

    /** Las cancelaciones se avisan siempre; {@code editable} es si tiene el perfil completo. */
    public SuscripcionesResponse aSuscripciones(Usuario usuario, boolean editable) {
        return new SuscripcionesResponse(true, usuario.recibeRecordatorios(), usuario.recibePromociones(), editable);
    }

    public AvisosAdminResponse aAvisosAdmin(AdminProfile avisos) {
        return new AvisosAdminResponse(avisos.getActivos(), avisos.getReservaConfirmada(), avisos.getSaldoCobrado(),
                avisos.getReservaCancelada(), avisos.getReembolsoManual());
    }
}
