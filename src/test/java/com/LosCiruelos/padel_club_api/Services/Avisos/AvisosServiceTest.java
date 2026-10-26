package com.LosCiruelos.padel_club_api.Services.Avisos;

import static com.LosCiruelos.padel_club_api.TestData.reserva;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.Entities.AvisosAdmin;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.CanalNotificacion;
import com.LosCiruelos.padel_club_api.reservas.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Repository.AvisosAdminRepository;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;
import com.LosCiruelos.padel_club_api.Services.Avisos.AvisoReserva.Destinatario;
import com.LosCiruelos.padel_club_api.Services.Email.EmailService;
import com.LosCiruelos.padel_club_api.Services.Email.EmailServiceFactory;
import com.LosCiruelos.padel_club_api.Services.WhatsApp.WhatsAppService;
import com.LosCiruelos.padel_club_api.Services.WhatsApp.WhatsAppServiceFactory;
import com.LosCiruelos.padel_club_api.common.Dinero;

class AvisosServiceTest {

    private UsuarioService usuarioService;
    private AvisosAdminRepository avisosAdminRepository;
    private WhatsAppService whatsApp;
    private EmailService email;
    private AvisosService avisos;

    private Usuario adminWhatsapp;
    private Usuario adminMail;

    @BeforeEach
    void setUp() {
        usuarioService = mock(UsuarioService.class);
        avisosAdminRepository = mock(AvisosAdminRepository.class);
        whatsApp = mock(WhatsAppService.class);
        WhatsAppServiceFactory fabricaWhatsApp = mock(WhatsAppServiceFactory.class);
        when(fabricaWhatsApp.getService()).thenReturn(whatsApp);
        email = mock(EmailService.class);
        EmailServiceFactory factory = mock(EmailServiceFactory.class);
        when(factory.getService()).thenReturn(email);
        when(whatsApp.estaConfigurado()).thenReturn(true);
        when(whatsApp.enviarPlantilla(anyString(), anyString(), anyList())).thenReturn(true);
        avisos = new AvisosService(usuarioService, avisosAdminRepository, fabricaWhatsApp, factory);

        adminWhatsapp = usuario(1L, Role.ADMIN);
        adminWhatsapp.setTelefono("5491122334455");
        adminWhatsapp.setTelefonoVerificado(true);
        adminWhatsapp.setCanalNotificaciones(CanalNotificacion.WHATSAPP);
        adminMail = usuario(2L, Role.ADMIN);
        when(usuarioService.adminsActivos()).thenReturn(List.of(adminWhatsapp, adminMail));
        when(avisosAdminRepository.findAllById(any())).thenReturn(List.of());
    }

    private AvisoReserva confirmada() {
        return AvisoReserva.de(TipoAviso.RESERVA_CONFIRMADA,
                reserva(10L, usuario(3L, Role.CLIENTE), EstadoReserva.SEÑADA, "15000"), new BigDecimal("7500"), null);
    }

    @Test
    void cadaAdminLoRecibePorElCanalQueEligio() {
        avisos.enviar(confirmada());

        verify(whatsApp).enviarPlantilla(eq("5491122334455"), eq("aviso_reserva_confirmada_v2"), anyList());
        verify(email).sendAviso(eq(adminMail.getEmail()), eq("Nueva reserva"), anyString());
        verify(email, never()).sendAviso(eq(adminWhatsapp.getEmail()), anyString(), anyString());
    }

    @Test
    void unAdminQueApagoEseAvisoNoLoRecibe() {
        AvisosAdmin sinConfirmadas = AvisosAdmin.porDefecto(2L);
        sinConfirmadas.setReservaConfirmada(false);
        when(avisosAdminRepository.findAllById(any())).thenReturn(List.of(sinConfirmadas));

        avisos.enviar(confirmada());

        verify(email, never()).sendAviso(anyString(), anyString(), anyString());
        verify(whatsApp).enviarPlantilla(anyString(), anyString(), anyList());
    }

    @Test
    void conLosAvisosDesactivadosNoRecibeNinguno() {
        AvisosAdmin pausados = AvisosAdmin.porDefecto(1L);
        pausados.setActivos(false);
        AvisosAdmin delOtro = AvisosAdmin.porDefecto(2L);
        delOtro.setActivos(false);
        when(avisosAdminRepository.findAllById(any())).thenReturn(List.of(pausados, delOtro));

        avisos.enviar(confirmada());

        verify(whatsApp, never()).enviarPlantilla(anyString(), anyString(), anyList());
        verify(email, never()).sendAviso(anyString(), anyString(), anyString());
    }

    @Test
    void sinTelefonoVerificadoOSinWhatsappConfiguradoVaPorMail() {
        Destinatario sinVerificar = new Destinatario("Ana", "ana@test.com", "5491100000000",
                CanalNotificacion.WHATSAPP, false);
        assertThat(avisos.canalPara(sinVerificar)).isEqualTo(CanalNotificacion.EMAIL);

        Destinatario verificado = new Destinatario("Ana", "ana@test.com", "5491100000000",
                CanalNotificacion.WHATSAPP, true);
        assertThat(avisos.canalPara(verificado)).isEqualTo(CanalNotificacion.WHATSAPP);

        when(whatsApp.estaConfigurado()).thenReturn(false);
        assertThat(avisos.canalPara(verificado)).isEqualTo(CanalNotificacion.EMAIL);
    }

    @Test
    void siWhatsappFallaSeMandaPorMail() {
        when(whatsApp.enviarPlantilla(anyString(), anyString(), anyList())).thenReturn(false);

        CanalNotificacion canal = avisos.entregar(Destinatario.de(adminWhatsapp), confirmada());

        assertThat(canal).isEqualTo(CanalNotificacion.EMAIL);
        verify(email).sendAviso(eq(adminWhatsapp.getEmail()), anyString(), anyString());
    }

    @Test
    void unErrorDelMailNoSePropaga() {
        org.mockito.Mockito.doThrow(new RuntimeException("smtp caído")).when(email)
                .sendAviso(anyString(), anyString(), anyString());

        assertThat(avisos.entregar(Destinatario.de(adminMail), confirmada())).isNull();
    }

    @Test
    void losAvisosParaElClienteVanSoloAlCliente() {
        Usuario cliente = usuario(3L, Role.CLIENTE);
        AvisoReserva recordatorio = AvisoReserva.de(TipoAviso.RECORDATORIO,
                reserva(10L, cliente, EstadoReserva.SEÑADA, "15000"), new BigDecimal("7500"), "Saldo a pagar: $7.500.");

        avisos.enviar(recordatorio);

        verify(email).sendAviso(eq(cliente.getEmail()), eq("Recordatorio de tu turno"), anyString());
        verify(usuarioService, never()).adminsActivos();
    }

    @Test
    void elTextoLlevaLosDatosDeLaReserva() {
        AvisoReserva aviso = confirmada();

        assertThat(aviso.parametros()).containsExactly("Nombre3", "1", "miércoles 14/10", "18:00 a 19:30",
                "$7.500", "$15.000");
        assertThat(aviso.texto()).isEqualTo(
                "Nueva reserva en Los Ciruelos: Nombre3 reservó la cancha 1 para el miércoles 14/10, de 18:00 a 19:30. "
                        + "Pagó $7.500 de un total de $15.000. Podés ver el detalle en el panel del club.");
        assertThat(Dinero.texto(new BigDecimal("7500.50"))).isEqualTo("$7.500,5");
    }
}
