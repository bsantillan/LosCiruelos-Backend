package com.LosCiruelos.padel_club_api.Services.Pagos;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.DTOs.PaymentResult;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPagoExterno;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;

import com.google.api.client.util.Value;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.preference.Preference;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service("mercadopago")
@RequiredArgsConstructor
public class MercadoPagoPaymentService implements PaymentService {

    @Value("${mercadopago.webhook-url}")
    private String webhookUrl;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public String iniciarPago(Reserva reserva, BigDecimal monto) {
        try {
            PreferenceItemRequest item = PreferenceItemRequest.builder()
                    .title("Reserva Cancha %d — %s %s-%s"
                            .formatted(
                                    reserva.getCancha().getNumero(),
                                    reserva.getFechaReserva(),
                                    reserva.getHoraInicio(),
                                    reserva.getHoraFin()))
                    .quantity(1)
                    .unitPrice(monto)
                    .currencyId("ARS")
                    .build();

            PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                    .items(List.of(item))
                    .notificationUrl(webhookUrl)
                    .externalReference(reserva.getId().toString())
                    .build();

            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(preferenceRequest);

            return preference.getId();

        } catch (MPApiException e) {
            log.error("Error de API de MP: {} — {}", e.getStatusCode(), e.getApiResponse());
            throw new ReservaException("Error al conectar con Mercado Pago. Intentá de nuevo.");
        } catch (MPException e) {
            log.error("Error de MP: {}", e.getMessage());
            throw new ReservaException("Error al conectar con Mercado Pago. Intentá de nuevo.");
        }
    }

    @Override
    public PaymentResult consultarPago(String paymentId) {
        try {
            PaymentClient paymentClient = new PaymentClient();
            Payment payment = paymentClient.get(Long.parseLong(paymentId));

            EstadoPagoExterno estado = switch (payment.getStatus()) {
                case "approved" -> EstadoPagoExterno.APROBADO;
                case "rejected", "cancelled" -> EstadoPagoExterno.RECHAZADO;
                case "pending", "in_process" -> EstadoPagoExterno.EN_PROCESO;
                case "refunded", "charged_back" -> EstadoPagoExterno.DEVUELTO;
                default -> throw new ReservaException(
                        "Status desconocido de MP: " + payment.getStatus());
            };

            return new PaymentResult(
                    estado,
                    payment.getExternalReference(),
                    payment.getTransactionAmount(),
                    null);
        } catch (MPApiException | MPException e) {
            log.error("Error de MP al consultar pago: {}", e.getMessage());
            throw new ReservaException("Error al consultar el pago en Mercado Pago.");
        }
    }

    @Override
    public PaymentResult procesarPago(Map<String, Object> formData) {
        try {
            PaymentClient client = new PaymentClient();

            PaymentCreateRequest createRequest = PaymentCreateRequest.builder()
                    .transactionAmount(new BigDecimal(formData.get("transaction_amount").toString()))
                    .token((String) formData.get("token"))
                    .installments(Integer.valueOf(formData.get("installments").toString()))
                    .paymentMethodId((String) formData.get("payment_method_id"))
                    .payer(PaymentPayerRequest.builder()
                            .email((String) ((Map<?, ?>) formData.get("payer")).get("email"))
                            .build())
                    .notificationUrl(webhookUrl)
                    .build();

            Payment payment = client.create(createRequest);

            EstadoPagoExterno estado = switch (payment.getStatus()) {
                case "approved" -> EstadoPagoExterno.APROBADO;
                case "rejected", "cancelled" -> EstadoPagoExterno.RECHAZADO;
                case "pending", "in_process" -> EstadoPagoExterno.EN_PROCESO;
                case "refunded", "charged_back" -> EstadoPagoExterno.DEVUELTO;
                default -> EstadoPagoExterno.EN_PROCESO;
            };

            return new PaymentResult(
                    estado,
                    payment.getExternalReference(),
                    payment.getTransactionAmount(),
                    payment.getId().toString());

        } catch (MPApiException e) {
            log.error("Error de API de MP al procesar pago Brick: {}", e.getStatusCode());
            throw new ReservaException("Error al procesar el pago. Intentá de nuevo.");
        } catch (MPException e) {
            log.error("Error de MP al procesar pago Brick: {}", e.getMessage());
            throw new ReservaException("Error al procesar el pago. Intentá de nuevo.");
        }
    }
}
