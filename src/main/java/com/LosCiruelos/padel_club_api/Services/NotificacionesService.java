package com.LosCiruelos.padel_club_api.Services;

import java.math.BigDecimal;
import java.time.LocalTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.DTOs.Requests.AvisosAdminRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.RecordatorioConfigRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.SuscripcionesRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.AvisosAdminResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.NotificacionesResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PruebaAvisoResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.RecordatorioConfigResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.SuscripcionesResponse;
import com.LosCiruelos.padel_club_api.Entities.AvisosAdmin;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.Exceptions.PerfilIncompletoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.AvisosAdminRepository;
import com.LosCiruelos.padel_club_api.Services.Avisos.AvisoReserva;
import com.LosCiruelos.padel_club_api.Services.Avisos.AvisoReserva.Destinatario;
import com.LosCiruelos.padel_club_api.Services.Avisos.AvisosService;
import com.LosCiruelos.padel_club_api.Services.Avisos.TipoAviso;
import com.LosCiruelos.padel_club_api.Services.WhatsApp.WhatsAppServiceFactory;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.usuarios.service.ClienteProfileService;
import com.LosCiruelos.padel_club_api.usuarios.service.TelefonoService;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;

import lombok.RequiredArgsConstructor;

/**
 * Preferencias de notificación de cada usuario (canal y teléfono), los avisos que elige cada
 * ADMIN y la configuración del recordatorio del turno.
 */
@Service
@RequiredArgsConstructor
public class NotificacionesService {

    private final UsuarioService usuarioService;
    private final TelefonoService telefonoService;
    private final ClienteProfileService clienteProfileService;
    private final AvisosAdminRepository avisosAdminRepository;
    private final AvisosService avisosService;
    private final ConfiguracionService configuracionService;
    private final WhatsAppServiceFactory whatsApp;
    private final Reloj reloj;

    // ── Canal y teléfono (cualquier rol) ─────────────────────────────────

    @Transactional(readOnly = true)
    public NotificacionesResponse obtener(String email) {
        return aResponse(usuario(email));
    }

    /**
     * Elige el canal y, opcionalmente, carga o cambia el teléfono. Elegir WhatsApp registra el
     * consentimiento y, si el teléfono no está verificado, le manda el código: hasta que lo
     * ingrese, los avisos siguen yendo por mail.
     */
    @Transactional
    public NotificacionesResponse actualizar(String email, CanalNotificacion canal, String telefono) {
        Usuario usuario = usuario(email);
        if (telefono != null && !telefono.isBlank()) {
            usuarioService.cambiarTelefono(usuario, telefono);
        }
        if (canal == CanalNotificacion.WHATSAPP) {
            if (usuario.getTelefono() == null) {
                throw new ReservaException("Cargá tu teléfono para recibir avisos por WhatsApp.");
            }
            if (usuario.getAceptaWhatsappAt() == null) {
                usuario.setAceptaWhatsappAt(reloj.ahora());
            }
        } else {
            usuario.setAceptaWhatsappAt(null);
        }
        usuario.setCanalNotificaciones(canal);
        usuarioService.save(usuario);

        if (canal == CanalNotificacion.WHATSAPP) {
            telefonoService.enviarCodigoSiCorresponde(usuario);
        }
        return aResponse(usuario);
    }

    @Transactional
    public void enviarCodigo(String email) {
        telefonoService.enviarCodigo(usuario(email));
    }

    @Transactional(noRollbackFor = com.LosCiruelos.padel_club_api.Exceptions.CodigoInvalidoException.class)
    public NotificacionesResponse verificarTelefono(String email, String codigo) {
        Usuario usuario = usuario(email);
        telefonoService.verificar(usuario, codigo);
        return aResponse(usuario);
    }

    // ── Avisos que elige el CLIENTE ──────────────────────────────────────

    @Transactional(readOnly = true)
    public SuscripcionesResponse suscripciones(String email) {
        Usuario cliente = usuario(email);
        return SuscripcionesResponse.from(cliente, clienteProfileService.perfilCompleto(cliente));
    }

    /** Recordatorios y novedades/promociones; solo con el perfil completo (celular verificado). */
    @Transactional
    public SuscripcionesResponse actualizarSuscripciones(String email, SuscripcionesRequest req) {
        Usuario cliente = usuario(email);
        if (!clienteProfileService.perfilCompleto(cliente)) {
            throw new PerfilIncompletoException("Completá tu perfil y verificá tu celular para elegir tus avisos.");
        }
        cliente.setRecibeRecordatorios(req.getRecordatorios());
        cliente.setRecibePromociones(req.getPromociones());
        usuarioService.save(cliente);
        return SuscripcionesResponse.from(cliente, true);
    }

    // ── Avisos del ADMIN ─────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AvisosAdminResponse avisosAdmin(String email) {
        Usuario admin = usuario(email);
        return AvisosAdminResponse.from(avisosAdminRepository.findById(admin.getId())
                .orElse(AvisosAdmin.porDefecto(admin.getId())));
    }

    @Transactional
    public AvisosAdminResponse actualizarAvisosAdmin(String email, AvisosAdminRequest req) {
        Usuario admin = usuario(email);
        AvisosAdmin avisos = avisosAdminRepository.findById(admin.getId())
                .orElse(AvisosAdmin.porDefecto(admin.getId()));
        if (req.getActivos() != null) {
            avisos.setActivos(req.getActivos());
        }
        avisos.setReservaConfirmada(req.getReservaConfirmada());
        avisos.setSaldoCobrado(req.getSaldoCobrado());
        avisos.setReservaCancelada(req.getReservaCancelada());
        avisos.setReembolsoManual(req.getReembolsoManual());
        return AvisosAdminResponse.from(avisosAdminRepository.save(avisos));
    }

    /** Manda un aviso de ejemplo al ADMIN por su canal, para comprobar que le llegan. */
    @Transactional(readOnly = true)
    public PruebaAvisoResponse probarAviso(String email) {
        Usuario admin = usuario(email);
        Destinatario destinatario = Destinatario.de(admin);
        AvisoReserva ejemplo = new AvisoReserva(TipoAviso.RESERVA_CONFIRMADA, 0L,
                new Destinatario("PRUEBA (cliente de ejemplo)", admin.getEmail(), null, CanalNotificacion.EMAIL, false),
                1, reloj.hoy(), LocalTime.of(18, 0), LocalTime.of(19, 30),
                new BigDecimal("15000"), new BigDecimal("7500"), null);
        CanalNotificacion canal = avisosService.entregar(destinatario, ejemplo);
        String destino = canal == CanalNotificacion.WHATSAPP ? admin.getTelefono() : admin.getEmail();
        return new PruebaAvisoResponse(canal != null, canal, destino);
    }

    // ── Recordatorio (configuración del club) ────────────────────────────

    public RecordatorioConfigResponse recordatorio() {
        Configuracion config = configuracionService.obtener();
        return new RecordatorioConfigResponse(config.getRecordatorioActivo(), config.getMinutosRecordatorio());
    }

    @Transactional
    public RecordatorioConfigResponse actualizarRecordatorio(RecordatorioConfigRequest req) {
        Configuracion config = configuracionService.obtener();
        config.setRecordatorioActivo(req.getRecordatorioActivo());
        config.setMinutosRecordatorio(req.getMinutosRecordatorio());
        Configuracion guardada = configuracionService.actualizar(config);
        return new RecordatorioConfigResponse(guardada.getRecordatorioActivo(), guardada.getMinutosRecordatorio());
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private NotificacionesResponse aResponse(Usuario usuario) {
        return new NotificacionesResponse(usuario.getCanalNotificaciones(),
                avisosService.canalPara(Destinatario.de(usuario)), usuario.getTelefono(),
                Boolean.TRUE.equals(usuario.getTelefonoVerificado()), whatsApp.getService().estaConfigurado());
    }

    private Usuario usuario(String email) {
        return usuarioService.autenticado(email);
    }
}
