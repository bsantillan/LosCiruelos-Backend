package com.LosCiruelos.padel_club_api.mapper;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.AvisosAdminResponse;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.MensajesWhatsAppResponse;
import com.LosCiruelos.padel_club_api.notificaciones.entity.enums.MotivoWhatsApp;
import com.LosCiruelos.padel_club_api.notificaciones.repository.ConteoWhatsApp;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.NotificacionesResponse;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.SuscripcionesResponse;
import com.LosCiruelos.padel_club_api.usuarios.entity.AdminProfile;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;

/**
 * Preferencias de aviso del usuario (canal y suscripciones) y de cada ADMIN, y los WhatsApp mandados →
 * sus respuestas.
 */
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

    /** Los conteos agrupados del período → los totales y los avisos por tipo. */
    public MensajesWhatsAppResponse aMensajesWhatsApp(LocalDate desde, LocalDate hasta, List<ConteoWhatsApp> conteos) {
        long codigos = 0;
        long avisos = 0;
        long aClientes = 0;
        Map<TipoAviso, Long> porAviso = new EnumMap<>(TipoAviso.class);
        for (ConteoWhatsApp c : conteos) {
            if (c.motivo() == MotivoWhatsApp.CODIGO) {
                codigos += c.cantidad();
            } else {
                avisos += c.cantidad();
                porAviso.merge(c.aviso(), c.cantidad(), Long::sum);
            }
            if (c.paraCliente()) {
                aClientes += c.cantidad();
            }
        }
        long total = codigos + avisos;
        List<MensajesWhatsAppResponse.PorAviso> lista = porAviso.entrySet().stream()
                .map(e -> new MensajesWhatsAppResponse.PorAviso(e.getKey(), e.getKey().asunto(), e.getValue()))
                .sorted(Comparator.comparingLong(MensajesWhatsAppResponse.PorAviso::cantidad).reversed()
                        .thenComparing(MensajesWhatsAppResponse.PorAviso::aviso))
                .toList();
        return new MensajesWhatsAppResponse(desde, hasta, total, codigos, avisos, aClientes, total - aClientes, lista);
    }
}
