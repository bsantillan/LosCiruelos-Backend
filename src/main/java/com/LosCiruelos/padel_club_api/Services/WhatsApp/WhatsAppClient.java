package com.LosCiruelos.padel_club_api.Services.WhatsApp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import lombok.extern.slf4j.Slf4j;

/**
 * Envía plantillas por la API oficial de WhatsApp (Cloud API de Meta).
 *
 * Solo se usan plantillas aprobadas en el WhatsApp Manager: son las únicas que Meta deja mandar
 * a alguien que no le escribió al club en las últimas 24 h. Un error de Meta se loguea y se
 * informa con {@code false}: nunca corta la operación que generó el mensaje.
 */
@Slf4j
@Component
public class WhatsAppClient {

    static final String URL_BASE = "https://graph.facebook.com";

    private final RestClient restClient;
    private final String token;
    private final String phoneNumberId;
    private final String version;
    private final String idioma;

    @Autowired
    public WhatsAppClient(@Value("${whatsapp.token:}") String token,
            @Value("${whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${whatsapp.api-version:v23.0}") String version,
            @Value("${whatsapp.idioma:es_AR}") String idioma) {
        this(RestClient.builder(), token, phoneNumberId, version, idioma);
    }

    WhatsAppClient(RestClient.Builder builder, String token, String phoneNumberId, String version, String idioma) {
        this.restClient = builder.baseUrl(URL_BASE).build();
        this.token = token;
        this.phoneNumberId = phoneNumberId;
        this.version = version;
        this.idioma = idioma;
        if (!estaConfigurado()) {
            log.warn("WhatsApp desactivado: faltan WHATSAPP_TOKEN o WHATSAPP_PHONE_NUMBER_ID. Los avisos van por mail.");
        }
    }

    public boolean estaConfigurado() {
        return token != null && !token.isBlank() && phoneNumberId != null && !phoneNumberId.isBlank();
    }

    /** Plantilla con parámetros de texto en el cuerpo ({{1}}, {{2}}, …). */
    public boolean enviarPlantilla(String telefono, String plantilla, List<String> parametros) {
        Map<String, Object> cuerpo = Map.of("type", "body", "parameters", textos(parametros));
        return enviar(telefono, plantilla, List.of(cuerpo));
    }

    /**
     * Plantilla de autenticación: el código va en el cuerpo y en el botón "Copiar código"
     * (Meta exige mandarlo en los dos).
     */
    public boolean enviarCodigo(String telefono, String plantilla, String codigo) {
        Map<String, Object> cuerpo = Map.of("type", "body", "parameters", textos(List.of(codigo)));
        Map<String, Object> boton = Map.of("type", "button", "sub_type", "url", "index", "0",
                "parameters", textos(List.of(codigo)));
        return enviar(telefono, plantilla, List.of(cuerpo, boton));
    }

    private boolean enviar(String telefono, String plantilla, List<Map<String, Object>> componentes) {
        if (!estaConfigurado()) {
            log.info("WhatsApp desactivado: no se envía '{}' a {}", plantilla, telefono);
            return false;
        }
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", plantilla);
        template.put("language", Map.of("code", idioma));
        template.put("components", componentes);

        Map<String, Object> mensaje = new LinkedHashMap<>();
        mensaje.put("messaging_product", "whatsapp");
        mensaje.put("to", telefono);
        mensaje.put("type", "template");
        mensaje.put("template", template);

        try {
            restClient.post()
                    .uri("/{version}/{id}/messages", version, phoneNumberId)
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(mensaje)
                    .retrieve()
                    .toBodilessEntity();
            log.info("WhatsApp '{}' enviado a {}", plantilla, telefono);
            return true;
        } catch (RestClientResponseException e) {
            log.error("Meta rechazó el WhatsApp '{}' a {}: {} — {}", plantilla, telefono, e.getStatusCode().value(),
                    e.getResponseBodyAsString());
            return false;
        } catch (RestClientException e) {
            log.error("No se pudo conectar con WhatsApp para enviar '{}' a {}: {}", plantilla, telefono,
                    e.getMessage());
            return false;
        }
    }

    /** Meta no acepta saltos de línea, tabs ni más de 4 espacios seguidos en los parámetros. */
    private static List<Map<String, Object>> textos(List<String> valores) {
        List<Map<String, Object>> parametros = new ArrayList<>();
        for (String valor : valores) {
            String limpio = valor == null || valor.isBlank() ? "-"
                    : valor.replaceAll("[\\r\\n\\t]+", " ").replaceAll(" {4,}", "   ").trim();
            parametros.add(Map.of("type", "text", "text", limpio));
        }
        return parametros;
    }
}
