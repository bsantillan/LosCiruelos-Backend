package com.LosCiruelos.padel_club_api.notificaciones.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;
import com.LosCiruelos.padel_club_api.notificaciones.entity.MensajeWhatsApp;
import com.LosCiruelos.padel_club_api.notificaciones.entity.enums.MotivoWhatsApp;

@DataJpaTest(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect" })
class MensajeWhatsAppRepositoryTest {

    private static final LocalDateTime OCTUBRE = LocalDateTime.of(2026, 10, 1, 0, 0);

    @Autowired
    private MensajeWhatsAppRepository repository;

    @Test
    void cuentaLosDelPeriodoAgrupadosPorMotivoAvisoYDestinatario() {
        repository.save(MensajeWhatsApp.codigo(true, OCTUBRE.plusHours(10)));
        repository.save(MensajeWhatsApp.codigo(true, OCTUBRE.plusDays(3)));
        repository.save(MensajeWhatsApp.aviso(TipoAviso.RECORDATORIO, true, OCTUBRE.plusDays(5)));
        repository.save(MensajeWhatsApp.aviso(TipoAviso.RESERVA_CONFIRMADA, false, OCTUBRE.plusDays(30).plusHours(23)));
        repository.save(MensajeWhatsApp.aviso(TipoAviso.RECORDATORIO, true, OCTUBRE.minusSeconds(1))); // septiembre
        repository.save(MensajeWhatsApp.codigo(false, OCTUBRE.plusMonths(1))); // noviembre

        var conteos = repository.contar(OCTUBRE, OCTUBRE.plusMonths(1));

        assertThat(conteos).containsExactlyInAnyOrder(
                new ConteoWhatsApp(MotivoWhatsApp.CODIGO, null, true, 2),
                new ConteoWhatsApp(MotivoWhatsApp.AVISO, TipoAviso.RECORDATORIO, true, 1),
                new ConteoWhatsApp(MotivoWhatsApp.AVISO, TipoAviso.RESERVA_CONFIRMADA, false, 1));
    }
}
