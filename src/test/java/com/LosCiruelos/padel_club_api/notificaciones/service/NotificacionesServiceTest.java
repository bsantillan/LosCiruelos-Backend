package com.LosCiruelos.padel_club_api.notificaciones.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.notificaciones.dto.request.AvisosAdminRequest;
import com.LosCiruelos.padel_club_api.notificaciones.dto.request.RecordatorioConfigRequest;
import com.LosCiruelos.padel_club_api.notificaciones.dto.request.SuscripcionesRequest;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.NotificacionesResponse;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.PruebaAvisoResponse;
import com.LosCiruelos.padel_club_api.notificaciones.entity.AvisosAdmin;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.Exceptions.PerfilIncompletoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.notificaciones.repository.AvisosAdminRepository;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.notificaciones.aviso.AvisoReserva;
import com.LosCiruelos.padel_club_api.notificaciones.whatsapp.WhatsAppService;
import com.LosCiruelos.padel_club_api.notificaciones.whatsapp.WhatsAppServiceFactory;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.usuarios.service.ClienteProfileService;
import com.LosCiruelos.padel_club_api.usuarios.service.TelefonoService;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;

class NotificacionesServiceTest {

    private UsuarioService usuarioService;
    private TelefonoService telefonoService;
    private ClienteProfileService clienteProfileService;
    private AvisosAdminRepository avisosAdminRepository;
    private AvisosService avisosService;
    private ConfiguracionRepository configuracionRepository;
    private NotificacionesService notificaciones;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        telefonoService = mock(TelefonoService.class);
        clienteProfileService = mock(ClienteProfileService.class);
        avisosAdminRepository = mock(AvisosAdminRepository.class);
        avisosService = mock(AvisosService.class);
        configuracionRepository = mock(ConfiguracionRepository.class);
        when(configuracionRepository.obtener()).thenReturn(TestData.configuracion());
        when(configuracionRepository.save(any(Configuracion.class))).thenAnswer(inv -> inv.getArgument(0));
        WhatsAppService whatsApp = mock(WhatsAppService.class);
        WhatsAppServiceFactory fabricaWhatsApp = mock(WhatsAppServiceFactory.class);
        when(fabricaWhatsApp.getService()).thenReturn(whatsApp);
        when(whatsApp.estaConfigurado()).thenReturn(true);
        notificaciones = new NotificacionesService(usuarioService, telefonoService, clienteProfileService, avisosAdminRepository,
                avisosService, new ConfiguracionService(configuracionRepository), fabricaWhatsApp, TestData.reloj(AHORA));

        usuario = usuario(1L, Role.CLIENTE);
        usuario.setTelefono("5491122334455");
        when(usuarioService.autenticado(usuario.getEmail())).thenReturn(usuario);
        when(avisosService.canalPara(any())).thenReturn(CanalNotificacion.EMAIL);
    }

    @Test
    void elegirWhatsappGuardaElConsentimientoYMandaElCodigo() {
        NotificacionesResponse r = notificaciones.actualizar(usuario.getEmail(), CanalNotificacion.WHATSAPP, null);

        assertThat(usuario.getCanalNotificaciones()).isEqualTo(CanalNotificacion.WHATSAPP);
        assertThat(usuario.getAceptaWhatsappAt()).isEqualTo(AHORA);
        verify(telefonoService).enviarCodigoSiCorresponde(usuario);
        // hasta verificar el teléfono sigue llegando por mail
        assertThat(r.canal()).isEqualTo(CanalNotificacion.WHATSAPP);
        assertThat(r.canalEnUso()).isEqualTo(CanalNotificacion.EMAIL);
        assertThat(r.whatsappDisponible()).isTrue();
    }

    @Test
    void conUnTelefonoNuevoLoGuardaAntesDeElegirWhatsapp() {
        notificaciones.actualizar(usuario.getEmail(), CanalNotificacion.WHATSAPP, "11 6123-4567");

        verify(usuarioService).cambiarTelefono(usuario, "11 6123-4567");
    }

    @Test
    void sinTelefonoNoSePuedeElegirWhatsapp() {
        usuario.setTelefono(null);

        assertThatThrownBy(() -> notificaciones.actualizar(usuario.getEmail(), CanalNotificacion.WHATSAPP, null))
                .isInstanceOf(ReservaException.class);
        verify(usuarioService, never()).save(any());
    }

    @Test
    void volverAlMailRetiraElConsentimiento() {
        usuario.setCanalNotificaciones(CanalNotificacion.WHATSAPP);
        usuario.setAceptaWhatsappAt(AHORA.minusDays(1));

        notificaciones.actualizar(usuario.getEmail(), CanalNotificacion.EMAIL, null);

        assertThat(usuario.getCanalNotificaciones()).isEqualTo(CanalNotificacion.EMAIL);
        assertThat(usuario.getAceptaWhatsappAt()).isNull();
        verify(telefonoService, never()).enviarCodigoSiCorresponde(any());
    }

    @Test
    void elClienteEligeSusAvisosSoloConElPerfilCompleto() {
        when(clienteProfileService.perfilCompleto(usuario)).thenReturn(false);
        SuscripcionesRequest req = new SuscripcionesRequest();
        req.setRecordatorios(false);
        req.setPromociones(true);

        var antes = notificaciones.suscripciones(usuario.getEmail());
        assertThat(antes.cancelaciones()).isTrue();
        assertThat(antes.recordatorios()).isTrue();
        assertThat(antes.promociones()).isFalse();
        assertThat(antes.editable()).isFalse();
        assertThatThrownBy(() -> notificaciones.actualizarSuscripciones(usuario.getEmail(), req))
                .isInstanceOf(PerfilIncompletoException.class);

        when(clienteProfileService.perfilCompleto(usuario)).thenReturn(true);
        var despues = notificaciones.actualizarSuscripciones(usuario.getEmail(), req);

        assertThat(despues.recordatorios()).isFalse();
        assertThat(despues.promociones()).isTrue();
        assertThat(despues.cancelaciones()).isTrue(); // siempre
        verify(usuarioService).save(usuario);
    }

    @Test
    void elAdminEligeQueAvisosRecibe() {
        Usuario admin = usuario(5L, Role.ADMIN);
        when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
        when(avisosAdminRepository.findById(5L)).thenReturn(Optional.empty());
        when(avisosAdminRepository.save(any(AvisosAdmin.class))).thenAnswer(inv -> inv.getArgument(0));

        // sin elegir nada recibe todo
        assertThat(notificaciones.avisosAdmin(admin.getEmail()).reservaConfirmada()).isTrue();

        AvisosAdminRequest req = new AvisosAdminRequest();
        req.setReservaConfirmada(false);
        req.setSaldoCobrado(true);
        req.setReservaCancelada(true);
        req.setReembolsoManual(true);
        var r = notificaciones.actualizarAvisosAdmin(admin.getEmail(), req);

        assertThat(r.activos()).isTrue();
        assertThat(r.reservaConfirmada()).isFalse();
        assertThat(r.reembolsoManual()).isTrue();

        // pausar todo conserva lo que eligió de cada aviso
        req.setActivos(false);
        r = notificaciones.actualizarAvisosAdmin(admin.getEmail(), req);
        assertThat(r.activos()).isFalse();
        assertThat(r.reembolsoManual()).isTrue();
        ArgumentCaptor<AvisosAdmin> guardado = ArgumentCaptor.forClass(AvisosAdmin.class);
        verify(avisosAdminRepository, org.mockito.Mockito.times(2)).save(guardado.capture());
        assertThat(guardado.getValue().getUsuarioId()).isEqualTo(5L);
    }

    @Test
    void elAvisoDePruebaSaleporElCanalDelAdmin() {
        Usuario admin = usuario(5L, Role.ADMIN);
        when(usuarioService.autenticado(admin.getEmail())).thenReturn(admin);
        when(avisosService.entregar(any(), any(AvisoReserva.class))).thenReturn(CanalNotificacion.EMAIL);

        PruebaAvisoResponse r = notificaciones.probarAviso(admin.getEmail());

        assertThat(r.enviado()).isTrue();
        assertThat(r.destino()).isEqualTo(admin.getEmail());
    }

    @Test
    void configuraElRecordatorio() {
        RecordatorioConfigRequest req = new RecordatorioConfigRequest();
        req.setRecordatorioActivo(false);
        req.setMinutosRecordatorio(60);

        var r = notificaciones.actualizarRecordatorio(req);

        assertThat(r.recordatorioActivo()).isFalse();
        assertThat(r.minutosRecordatorio()).isEqualTo(60);
        verify(configuracionRepository).save(any(Configuracion.class));
    }
}
