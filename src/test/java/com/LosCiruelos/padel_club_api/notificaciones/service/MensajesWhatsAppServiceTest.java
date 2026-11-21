package com.LosCiruelos.padel_club_api.notificaciones.service;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.NotificacionesMapper;
import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.MensajesWhatsAppResponse;
import com.LosCiruelos.padel_club_api.notificaciones.entity.MensajeWhatsApp;
import com.LosCiruelos.padel_club_api.notificaciones.entity.enums.MotivoWhatsApp;
import com.LosCiruelos.padel_club_api.notificaciones.repository.ConteoWhatsApp;
import com.LosCiruelos.padel_club_api.notificaciones.repository.MensajeWhatsAppRepository;

/** Los WhatsApp que manda el club: cada uno se registra y el ADMIN ve los totales del período. */
class MensajesWhatsAppServiceTest {

    private MensajeWhatsAppRepository repository;
    private MensajesWhatsAppService mensajes;

    @BeforeEach
    void setUp() {
        repository = mock(MensajeWhatsAppRepository.class);
        mensajes = new MensajesWhatsAppService(repository, new NotificacionesMapper(), TestData.reloj(AHORA));
    }

    @Test
    void registraElCodigoYElAvisoConLaHoraYElDestinatario() {
        mensajes.registrarCodigo(true);
        mensajes.registrarAviso(TipoAviso.RECORDATORIO, true);

        ArgumentCaptor<MensajeWhatsApp> guardados = ArgumentCaptor.forClass(MensajeWhatsApp.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(2)).save(guardados.capture());
        MensajeWhatsApp codigo = guardados.getAllValues().get(0);
        assertThat(codigo.getMotivo()).isEqualTo(MotivoWhatsApp.CODIGO);
        assertThat(codigo.getAviso()).isNull();
        assertThat(codigo.getParaCliente()).isTrue();
        assertThat(codigo.getEnviadoAt()).isEqualTo(AHORA);
        assertThat(guardados.getAllValues().get(1).getAviso()).isEqualTo(TipoAviso.RECORDATORIO);
    }

    @Test
    void unErrorAlRegistrarNoCortaElEnvio() {
        when(repository.save(any())).thenThrow(new RuntimeException("sin base"));

        mensajes.registrarCodigo(false); // no lanza
    }

    @Test
    void sinFechasEsDelUnoDelMesAHoyYSumaPorMotivoYDestinatario() {
        when(repository.contar(any(), any())).thenReturn(List.of(
                new ConteoWhatsApp(MotivoWhatsApp.CODIGO, null, true, 4),
                new ConteoWhatsApp(MotivoWhatsApp.AVISO, TipoAviso.RECORDATORIO, true, 10),
                new ConteoWhatsApp(MotivoWhatsApp.AVISO, TipoAviso.RESERVA_CONFIRMADA, false, 3),
                new ConteoWhatsApp(MotivoWhatsApp.CODIGO, null, false, 1)));

        MensajesWhatsAppResponse r = mensajes.resumen(null, null);

        // TestData: hoy es el martes 13/10/2026
        assertThat(r.desde()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(r.hasta()).isEqualTo(LocalDate.of(2026, 10, 13));
        verify(repository).contar(LocalDate.of(2026, 10, 1).atStartOfDay(), LocalDate.of(2026, 10, 14).atStartOfDay());
        assertThat(r.total()).isEqualTo(18);
        assertThat(r.codigos()).isEqualTo(5);
        assertThat(r.avisos()).isEqualTo(13);
        assertThat(r.aClientes()).isEqualTo(14);
        assertThat(r.alPersonal()).isEqualTo(4);
        assertThat(r.porAviso()).extracting(MensajesWhatsAppResponse.PorAviso::aviso)
                .containsExactly(TipoAviso.RECORDATORIO, TipoAviso.RESERVA_CONFIRMADA);
        assertThat(r.porAviso().get(0).nombre()).isEqualTo(TipoAviso.RECORDATORIO.asunto());
    }

    @Test
    void conUnaSolaFechaYConElRangoAlReves() {
        when(repository.contar(any(), any())).thenReturn(List.of());

        MensajesWhatsAppResponse soloHasta = mensajes.resumen(null, LocalDate.of(2026, 9, 20));
        assertThat(soloHasta.desde()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(soloHasta.total()).isZero();
        assertThat(soloHasta.porAviso()).isEmpty();
        assertThat(mensajes.resumen(LocalDate.of(2026, 10, 5), null).hasta()).isEqualTo(LocalDate.of(2026, 10, 13));

        assertThatThrownBy(() -> mensajes.resumen(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La fecha desde no puede ser posterior a hasta.");
    }
}
