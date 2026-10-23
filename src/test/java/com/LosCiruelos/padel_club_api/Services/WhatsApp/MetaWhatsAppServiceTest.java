package com.LosCiruelos.padel_club_api.Services.WhatsApp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MetaWhatsAppServiceTest {

    private static final String URL = "https://graph.facebook.com/v23.0/123456/messages";

    private MockRestServiceServer meta;
    private MetaWhatsAppService cliente;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        meta = MockRestServiceServer.bindTo(builder).build();
        cliente = new MetaWhatsAppService(builder, "token-secreto", "123456", "v23.0", "es_AR");
    }

    @Test
    void mandaLaPlantillaConSusParametros() {
        meta.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-secreto"))
                .andExpect(content().json("""
                        {"messaging_product":"whatsapp","to":"54111522334455","type":"template",
                         "template":{"name":"recordatorio_turno","language":{"code":"es_AR"},
                           "components":[{"type":"body","parameters":[
                             {"type":"text","text":"Ana"},{"type":"text","text":"línea uno línea dos"},
                             {"type":"text","text":"-"}]}]}}
                        """, true))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.1\"}]}", MediaType.APPLICATION_JSON));

        boolean enviado = cliente.enviarPlantilla("5491122334455", "recordatorio_turno",
                java.util.Arrays.asList("Ana", "línea uno\nlínea dos", null));

        assertThat(enviado).isTrue();
        meta.verify();
    }

    @Test
    void elCodigoVaEnElCuerpoYEnElBoton() {
        meta.expect(requestTo(URL))
                .andExpect(content().json("""
                        {"template":{"name":"codigo_verificacion","components":[
                          {"type":"body","parameters":[{"type":"text","text":"123456"}]},
                          {"type":"button","sub_type":"url","index":"0","parameters":[{"type":"text","text":"123456"}]}]}}
                        """))
                .andRespond(withSuccess());

        assertThat(cliente.enviarCodigo("5491122334455", "codigo_verificacion", "123456")).isTrue();
        meta.verify();
    }

    @Test
    void unErrorDeMetaNoLanzaExcepcion() {
        meta.expect(requestTo(URL)).andRespond(withBadRequest()
                .body("{\"error\":{\"code\":131026,\"message\":\"Message undeliverable\"}}")
                .contentType(MediaType.APPLICATION_JSON));

        assertThat(cliente.enviarPlantilla("5491122334455", "recordatorio_turno", List.of("Ana"))).isFalse();
    }

    @Test
    void sinConfiguracionNoLlamaAMeta() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer sinLlamadas = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppService desactivado = new MetaWhatsAppService(builder, "", "", "v23.0", "es_AR");

        assertThat(desactivado.estaConfigurado()).isFalse();
        assertThat(desactivado.enviarPlantilla("5491122334455", "recordatorio_turno", List.of("Ana"))).isFalse();
        sinLlamadas.verify();
    }
}
