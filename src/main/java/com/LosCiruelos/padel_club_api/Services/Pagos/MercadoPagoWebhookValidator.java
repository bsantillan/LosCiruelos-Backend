package com.LosCiruelos.padel_club_api.Services.Pagos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Valida la firma {@code x-signature} que Mercado Pago agrega a los webhooks.
 *
 * Manifiesto firmado: {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}
 * con HMAC-SHA256 y la clave secreta configurada en "Tus integraciones → Webhooks".
 *
 * Aun sin firma, el webhook nunca confía en el contenido de la notificación:
 * siempre vuelve a consultar el pago a la API de MP con nuestro access token.
 */
@Slf4j
@Component
public class MercadoPagoWebhookValidator {

    private final String secret;

    public MercadoPagoWebhookValidator(@Value("${mercadopago.webhook-secret:}") String secret) {
        this.secret = secret;
        if (!estaConfigurado()) {
            log.warn("MP_WEBHOOK_SECRET no configurado: no se valida la firma de los webhooks de Mercado Pago");
        }
    }

    public boolean estaConfigurado() {
        return secret != null && !secret.isBlank();
    }

    /**
     * @return true si la firma es válida, o si no hay clave configurada.
     */
    public boolean esValida(String xSignature, String xRequestId, String dataId) {
        if (!estaConfigurado()) {
            return true;
        }
        if (xSignature == null || xSignature.isBlank()) {
            return false;
        }

        String ts = null;
        String v1 = null;
        for (String parte : xSignature.split(",")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length != 2) {
                continue;
            }
            if (kv[0].equals("ts")) {
                ts = kv[1];
            } else if (kv[0].equals("v1")) {
                v1 = kv[1];
            }
        }
        if (ts == null || v1 == null) {
            return false;
        }

        StringBuilder manifiesto = new StringBuilder();
        if (dataId != null && !dataId.isBlank()) {
            // MP firma el id en minúsculas cuando es alfanumérico
            manifiesto.append("id:").append(dataId.toLowerCase()).append(';');
        }
        if (xRequestId != null && !xRequestId.isBlank()) {
            manifiesto.append("request-id:").append(xRequestId).append(';');
        }
        manifiesto.append("ts:").append(ts).append(';');

        String esperado = hmacSha256(manifiesto.toString());
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                v1.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256(String datos) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(datos.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo calcular la firma HMAC", e);
        }
    }
}
