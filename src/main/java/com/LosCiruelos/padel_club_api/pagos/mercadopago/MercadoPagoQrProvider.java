package com.LosCiruelos.padel_club_api.pagos.mercadopago;

import java.math.BigDecimal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.exception.PagoProveedorException;
import com.LosCiruelos.padel_club_api.pagos.exception.ReembolsoFallidoException;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr;

import lombok.extern.slf4j.Slf4j;

/**
 * QR de la caja con la API de Orders de Mercado Pago (POST/GET /v1/orders, /cancel y /refund), en
 * modo estático: la order se asigna a la caja {@code MP_QR_EXTERNAL_POS_ID}, cuyo QR está impreso en
 * el mostrador, y el cliente la paga al escanearlo. La caja se da de alta una sola vez (ver
 * docs/mercado-pago-qr.md). Todas las llamadas llevan X-Idempotency-Key derivada del UUID del pago.
 */
@Slf4j
@Component
public class MercadoPagoQrProvider implements ProveedorCobroQr {

    private static final String ERROR_MP = "Error al conectar con Mercado Pago. Intentá de nuevo.";

    private final RestClient api;
    private final String externalPosId;
    private final boolean configurado;

    @Autowired
    public MercadoPagoQrProvider(@Value("${mercadopago.api-url:https://api.mercadopago.com}") String apiUrl,
            @Value("${mercadopago.access-token:}") String accessToken,
            @Value("${mercadopago.qr.external-pos-id:}") String externalPosId) {
        this(RestClient.builder().requestFactory(conTiempos()), apiUrl, accessToken, externalPosId);
    }

    MercadoPagoQrProvider(RestClient.Builder builder, String apiUrl, String accessToken, String externalPosId) {
        this.api = builder.baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();
        this.externalPosId = externalPosId;
        this.configurado = accessToken != null && !accessToken.isBlank() && externalPosId != null
                && !externalPosId.isBlank();
    }

    private static SimpleClientHttpRequestFactory conTiempos() {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(5_000);
        fabrica.setReadTimeout(15_000);
        return fabrica;
    }

    @Override
    public boolean estaConfigurado() {
        return configurado;
    }

    @Override
    public String crear(Pago pago, String descripcion, int minutos) {
        String monto = monto(pago.getMonto());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "qr");
        // sin datos personales: solo ids internos
        body.put("external_reference", "reserva-" + pago.getReserva().getId() + "-pago-" + pago.getId());
        body.put("total_amount", monto);
        body.put("description", descripcion);
        body.put("expiration_time", "PT" + minutos + "M");
        body.put("config", Map.of("qr", Map.of("external_pos_id", externalPosId, "mode", "static")));
        body.put("transactions", Map.of("payments", List.of(Map.of("amount", monto))));
        try {
            Map<?, ?> respuesta = api.post().uri("/v1/orders")
                    .header("X-Idempotency-Key", clave("qr", pago))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            String orderId = texto(respuesta, "id");
            if (orderId == null) {
                log.error("MP creó la order del pago #{} sin id: {}", pago.getId(), respuesta);
                throw new PagoProveedorException(ERROR_MP);
            }
            return orderId;
        } catch (RestClientResponseException e) {
            log.error("Error de MP creando la order del pago #{}: {} — {}", pago.getId(), e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw new PagoProveedorException(ERROR_MP);
        } catch (RestClientException e) {
            log.error("Error de MP creando la order del pago #{}: {}", pago.getId(), e.getMessage());
            throw new PagoProveedorException(ERROR_MP);
        }
    }

    @Override
    public EstadoOrdenQr consultar(String orderId) {
        try {
            Map<?, ?> orden = api.get().uri("/v1/orders/{id}", orderId).retrieve().body(Map.class);
            return estado(orden);
        } catch (RestClientResponseException e) {
            log.error("Error de MP consultando la order {}: {} — {}", orderId, e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw new PagoProveedorException(ERROR_MP);
        } catch (RestClientException e) {
            log.error("Error de MP consultando la order {}: {}", orderId, e.getMessage());
            throw new PagoProveedorException(ERROR_MP);
        }
    }

    @Override
    public void cancelar(Pago pago) {
        try {
            api.post().uri("/v1/orders/{id}/cancel", pago.getOrderId())
                    .header("X-Idempotency-Key", clave("qrcancel", pago))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // 409: la order ya no está "created" (se pagó, venció o el pago está en curso)
            if (e.getStatusCode().value() == HttpStatus.CONFLICT.value()) {
                throw new OrdenNoCancelableException(e.getResponseBodyAsString());
            }
            log.error("Error de MP cancelando la order {}: {} — {}", pago.getOrderId(), e.getStatusCode(),
                    e.getResponseBodyAsString());
            throw new PagoProveedorException(ERROR_MP);
        } catch (RestClientException e) {
            log.error("Error de MP cancelando la order {}: {}", pago.getOrderId(), e.getMessage());
            throw new PagoProveedorException(ERROR_MP);
        }
    }

    @Override
    public void reembolsar(Pago pago, BigDecimal monto) {
        try {
            RestClient.RequestBodySpec pedido = api.post().uri("/v1/orders/{id}/refund", pago.getOrderId())
                    .header("X-Idempotency-Key", clave("qrrefund", pago));
            if (monto.compareTo(pago.getMonto()) < 0) {
                // parcial: hay que indicar el pago dentro de la order y el monto
                String pagoId = consultar(pago.getOrderId()).pagoId();
                pedido = pedido.contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("transactions", List.of(Map.of("id", pagoId, "amount", monto(monto)))));
            }
            pedido.retrieve().toBodilessEntity();
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            log.error("MP no reembolsó la order {} (${}): {} — {}", pago.getOrderId(), monto, status,
                    e.getResponseBodyAsString());
            // 4xx (salvo 425: todavía no se puede) no se arregla reintentando
            throw new ReembolsoFallidoException(e.getStatusCode().is4xxClientError() && status != 425);
        } catch (RestClientException e) {
            log.error("MP no respondió al reembolso de la order {}: {}", pago.getOrderId(), e.getMessage());
            throw new ReembolsoFallidoException(false);
        }
    }

    /** Status de la order → estado; el monto cobrado, del pago de la order. */
    static EstadoOrdenQr estado(Map<?, ?> orden) {
        String status = texto(orden, "status");
        EstadoOrden estado = switch (status == null ? "" : status) {
            case "processed" -> EstadoOrden.PROCESADA;
            case "canceled" -> EstadoOrden.CANCELADA;
            case "expired" -> EstadoOrden.VENCIDA;
            case "refunded" -> EstadoOrden.DEVUELTA;
            default -> EstadoOrden.CREADA; // created o un estado intermedio: sigue esperando
        };
        Map<?, ?> pago = primerPago(orden);
        BigDecimal montoPagado = null;
        if (estado != EstadoOrden.CREADA && pago != null) {
            String pagado = texto(pago, "paid_amount");
            montoPagado = new BigDecimal(pagado != null ? pagado : texto(pago, "amount"));
        }
        return new EstadoOrdenQr(estado, montoPagado, pago != null ? texto(pago, "id") : null);
    }

    private static Map<?, ?> primerPago(Map<?, ?> orden) {
        if (orden != null && orden.get("transactions") instanceof Map<?, ?> tx
                && tx.get("payments") instanceof List<?> pagos && !pagos.isEmpty()
                && pagos.get(0) instanceof Map<?, ?> pago) {
            return pago;
        }
        return null;
    }

    private static String texto(Map<?, ?> mapa, String clave) {
        Object valor = mapa != null ? mapa.get(clave) : null;
        return valor != null ? valor.toString() : null;
    }

    /** Montos como string con 2 decimales, como pide la API ("15000.00"). */
    private static String monto(BigDecimal monto) {
        return Dinero.redondear(monto).toPlainString();
    }

    private static String clave(String operacion, Pago pago) {
        return "losciruelos-" + operacion + "-" + pago.getClaveIdempotencia();
    }
}
