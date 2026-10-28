package com.LosCiruelos.padel_club_api.notificaciones.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WhatsAppServiceFactoryTest {

    private final WhatsAppService meta = mock(WhatsAppService.class);

    private WhatsAppServiceFactory fabrica(String proveedor) {
        WhatsAppServiceFactory fabrica = new WhatsAppServiceFactory(Map.of("meta", meta));
        ReflectionTestUtils.setField(fabrica, "provider", proveedor);
        return fabrica;
    }

    @Test
    void eligeElProveedorConfigurado() {
        assertThat(fabrica("meta").getService()).isSameAs(meta);
    }

    @Test
    void unProveedorDesconocidoEsUnErrorDeConfiguracion() {
        assertThatThrownBy(() -> fabrica("twilio").getService())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("twilio");
    }
}
