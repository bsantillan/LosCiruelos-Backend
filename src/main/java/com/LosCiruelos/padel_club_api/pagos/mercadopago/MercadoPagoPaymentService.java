package com.LosCiruelos.padel_club_api.pagos.mercadopago;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.pagos.payment.PaymentResult;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentService;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.pagos.payment.EstadoPagoExterno;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.Exceptions.PagoNoEncontradoException;
import com.LosCiruelos.padel_club_api.Exceptions.PagoProveedorException;
import com.LosCiruelos.padel_club_api.Exceptions.ReembolsoFallidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.common.Fechas;
import com.mercadopago.client.common.IdentificationRequest;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service("mercadopago")
public class MercadoPagoPaymentService implements PaymentService {

    static final String METADATA_PAGO_ID = "pago_id";

    private final String webhookUrl;
    private final String frontendUrl;

    // Antes se usaba com.google.api.client.util.Value (de Google) y la URL quedaba en null
    public MercadoPagoPaymentService(@Value("${mercadopago.webhook-url}") String webhookUrl,
            @Value("${app.frontend-url}") String frontendUrl) {
        this.webhookUrl = webhookUrl;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }

    @Override
    public String crearPreferencia(Pago pago) {
        try {
            PreferenceRequest preferenceRequest = armarPreferencia(pago);
            Preference preference = new PreferenceClient().create(preferenceRequest, opciones(pago, "pref"));
            return preference.getId();
        } catch (MPApiException e) {
            log.error("Error de API de MP creando la preferencia del pago #{}: {} — {}", pago.getId(),
                    e.getStatusCode(), contenido(e));
            throw new PagoProveedorException("Error al conectar con Mercado Pago. Intentá de nuevo.");
        } catch (MPException e) {
            log.error("Error de MP creando la preferencia del pago #{}: {}", pago.getId(), e.getMessage());
            throw new PagoProveedorException("Error al conectar con Mercado Pago. Intentá de nuevo.");
        }
    }

    /**
     * Preferencia para pagar con la cuenta de Mercado Pago (Wallet Brick) o el Payment Brick:
     * <ul>
     * <li>back_urls: al terminar, MP devuelve al cliente a {@code FRONTEND_URL/pago/resultado}
     * (MP agrega status, payment_id, external_reference, etc.); auto_return solo con https,
     * porque MP no lo acepta con URLs locales;</li>
     * <li>binary_mode: el pago queda aprobado o rechazado en el momento, nunca pendiente;</li>
     * <li>si la reserva está PENDIENTE, la preferencia vence con ella: MP no deja pagar fuera de plazo.</li>
     * </ul>
     */
    PreferenceRequest armarPreferencia(Pago pago) {
        Reserva reserva = pago.getReserva();
        PreferenceItemRequest item = PreferenceItemRequest.builder()
                .title(descripcion(reserva))
                .quantity(1)
                .unitPrice(pago.getMonto())
                .currencyId("ARS")
                .build();

        String retorno = frontendUrl + "/pago/resultado?reservaId=" + reserva.getId() + "&resultado=";
        PreferenceRequest.PreferenceRequestBuilder builder = PreferenceRequest.builder()
                .items(List.of(item))
                .notificationUrl(webhookUrl)
                .externalReference(reserva.getId().toString())
                .metadata(Map.of(METADATA_PAGO_ID, pago.getId().toString()))
                .binaryMode(true)
                .backUrls(PreferenceBackUrlsRequest.builder()
                        .success(retorno + "aprobado")
                        .failure(retorno + "rechazado")
                        .pending(retorno + "pendiente")
                        .build());

        if (frontendUrl.startsWith("https://")) {
            builder.autoReturn("approved");
        }
        if (reserva.getEstado() == EstadoReserva.PENDIENTE && reserva.getExpiresAt() != null) {
            builder.expires(true)
                    .expirationDateTo(reserva.getExpiresAt().atZone(Fechas.ZONA).toOffsetDateTime());
        }
        return builder.build();
    }

    @Override
    public PaymentResult cobrar(Pago pago, Map<String, Object> formData) {
        Reserva reserva = pago.getReserva();
        String token = texto(formData, "token");
        String paymentMethodId = texto(formData, "payment_method_id");
        if (token == null || paymentMethodId == null) {
            throw new ReservaException("Faltan los datos de la tarjeta (token y payment_method_id).");
        }

        try {
            PaymentCreateRequest createRequest = PaymentCreateRequest.builder()
                    // el monto lo define el servidor: nunca se toma transaction_amount del front
                    .transactionAmount(pago.getMonto())
                    .token(token)
                    .installments(entero(formData, "installments", 1))
                    .paymentMethodId(paymentMethodId)
                    .issuerId(texto(formData, "issuer_id"))
                    .description(descripcion(reserva))
                    .externalReference(reserva.getId().toString())
                    .metadata(Map.of(METADATA_PAGO_ID, pago.getId().toString()))
                    .payer(payer(formData, reserva))
                    .notificationUrl(webhookUrl)
                    // aprobado o rechazado en el momento, sin quedar en revisión
                    .binaryMode(true)
                    .build();

            // misma clave para el mismo Pago: si el pedido se repite, MP no cobra dos veces
            Payment payment = new PaymentClient().create(createRequest, opciones(pago, "cobro"));
            return aResultado(payment);
        } catch (MPApiException e) {
            log.error("Error de API de MP al cobrar el pago #{}: {} — {}", pago.getId(), e.getStatusCode(), contenido(e));
            if (e.getStatusCode() >= 400 && e.getStatusCode() < 500) {
                throw new ReservaException("Mercado Pago rechazó los datos del pago. Revisá la tarjeta e intentá de nuevo.");
            }
            throw new PagoProveedorException("Error al procesar el pago. Intentá de nuevo.");
        } catch (MPException e) {
            log.error("Error de MP al cobrar el pago #{}: {}", pago.getId(), e.getMessage());
            throw new PagoProveedorException("Error al procesar el pago. Intentá de nuevo.");
        }
    }

    @Override
    public PaymentResult consultarPago(String paymentId) {
        long id;
        try {
            id = Long.parseLong(paymentId);
        } catch (NumberFormatException e) {
            throw new ReservaException("Id de pago inválido: " + paymentId);
        }
        try {
            return aResultado(new PaymentClient().get(id));
        } catch (MPApiException e) {
            if (e.getStatusCode() == 404) {
                // pago inexistente (ej. "Simular notificación" del panel de MP): no tiene sentido reintentar
                throw new PagoNoEncontradoException("El pago " + paymentId + " no existe en Mercado Pago.");
            }
            log.error("Error de API de MP al consultar el pago {}: {} — {}", paymentId, e.getStatusCode(), contenido(e));
            throw new PagoProveedorException("Error al consultar el pago en Mercado Pago.");
        } catch (MPException e) {
            log.error("Error de MP al consultar el pago {}: {}", paymentId, e.getMessage());
            throw new PagoProveedorException("Error al consultar el pago en Mercado Pago.");
        }
    }

    @Override
    public void reembolsar(Pago pago, BigDecimal monto) {
        try {
            // misma clave en cada reintento: MP nunca devuelve dos veces el mismo reembolso
            new PaymentRefundClient().refund(Long.parseLong(pago.getExternalPaymentId()), monto,
                    opciones(pago, "reembolso"));
            log.info("Reembolso de ${} del pago #{} (MP {})", monto, pago.getId(), pago.getExternalPaymentId());
        } catch (MPApiException e) {
            log.warn("MP no pudo reembolsar el pago #{}: {} — {}", pago.getId(), e.getStatusCode(), contenido(e));
            // 404: el pago no existe en MP; reintentar no sirve
            throw new ReembolsoFallidoException(e.getStatusCode() == 404);
        } catch (MPException e) {
            log.warn("Error de MP al reembolsar el pago #{}: {}", pago.getId(), e.getMessage());
            throw new ReembolsoFallidoException(false);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    static EstadoPagoExterno mapearEstado(String status) {
        if (status == null) {
            return EstadoPagoExterno.EN_PROCESO;
        }
        return switch (status) {
            case "approved" -> EstadoPagoExterno.APROBADO;
            case "rejected", "cancelled" -> EstadoPagoExterno.RECHAZADO;
            case "refunded", "charged_back" -> EstadoPagoExterno.DEVUELTO;
            // pending, in_process, in_mediation, authorized: todavía no hay resultado final
            default -> EstadoPagoExterno.EN_PROCESO;
        };
    }

    static Long pagoIdDesdeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.get(METADATA_PAGO_ID) == null) {
            return null;
        }
        try {
            // MP puede devolverlo como "12", 12 o 12.0
            return new BigDecimal(metadata.get(METADATA_PAGO_ID).toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private PaymentResult aResultado(Payment payment) {
        return PaymentResult.builder()
                .estado(mapearEstado(payment.getStatus()))
                .externalReference(payment.getExternalReference())
                .pagoId(pagoIdDesdeMetadata(payment.getMetadata()))
                .monto(payment.getTransactionAmount())
                .montoDevuelto(payment.getTransactionAmountRefunded())
                .paymentId(payment.getId() != null ? payment.getId().toString() : null)
                .detalle(payment.getStatusDetail())
                .build();
    }

    /**
     * Clave de idempotencia por pago y operación. Usa el UUID del pago y no su id: si la base
     * se recrea los ids se repiten, y MP respondería con el resultado de un pedido viejo.
     */
    static String claveIdempotencia(Pago pago, String operacion) {
        return "losciruelos-" + operacion + "-" + pago.getClaveIdempotencia();
    }

    private MPRequestOptions opciones(Pago pago, String operacion) {
        return MPRequestOptions.builder()
                .customHeaders(Map.of("X-Idempotency-Key", claveIdempotencia(pago, operacion)))
                .build();
    }

    private PaymentPayerRequest payer(Map<String, Object> formData, Reserva reserva) {
        Map<?, ?> payer = formData.get("payer") instanceof Map<?, ?> datos ? datos : Map.of();
        String email = payer.get("email") != null ? payer.get("email").toString() : reserva.getCliente().getEmail();

        PaymentPayerRequest.PaymentPayerRequestBuilder builder = PaymentPayerRequest.builder().email(email);
        if (payer.get("identification") instanceof Map<?, ?> doc && doc.get("type") != null && doc.get("number") != null) {
            builder.identification(IdentificationRequest.builder()
                    .type(doc.get("type").toString())
                    .number(doc.get("number").toString())
                    .build());
        }
        return builder.build();
    }

    private String descripcion(Reserva reserva) {
        return "Reserva Cancha %d — %s %s-%s".formatted(
                reserva.getCancha().getNumero(),
                reserva.getFechaReserva(),
                reserva.getHoraInicio(),
                reserva.getHoraFin());
    }

    private static String contenido(MPApiException e) {
        return e.getApiResponse() != null ? e.getApiResponse().getContent() : "";
    }

    private static String texto(Map<String, Object> data, String clave) {
        Object valor = data.get(clave);
        return valor != null ? valor.toString() : null;
    }

    private static int entero(Map<String, Object> data, String clave, int porDefecto) {
        Object valor = data.get(clave);
        if (valor == null) {
            return porDefecto;
        }
        try {
            return new BigDecimal(valor.toString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw new ReservaException("Valor inválido para " + clave);
        }
    }
}
