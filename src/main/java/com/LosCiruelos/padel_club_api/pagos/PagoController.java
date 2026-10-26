package com.LosCiruelos.padel_club_api.pagos;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.pagos.dto.BonificacionRequest;
import com.LosCiruelos.padel_club_api.pagos.dto.PagoManualRequest;
import com.LosCiruelos.padel_club_api.pagos.dto.PagoResponse;
import com.LosCiruelos.padel_club_api.Exceptions.PagoNoEncontradoException;
import com.LosCiruelos.padel_club_api.pagos.mercadopago.MercadoPagoWebhookValidator;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;
    private final MercadoPagoWebhookValidator webhookValidator;

    /** Crea el pago (seña o total) y la preferencia de MP. Devuelve el preference_id en paymentToken. */
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'EMPLEADO')")
    @PostMapping("/{reservaId}/iniciar")
    public ResponseEntity<PagoResponse> iniciarPago(
            @PathVariable Long reservaId,
            @RequestParam(defaultValue = "false") Boolean pagarTotal,
            Authentication authentication) {
        return ResponseEntity.ok(pagoService.iniciarPago(reservaId, pagarTotal, authentication.getName()));
    }

    /** Cobra con el formData del Payment Brick (token de tarjeta, cuotas, medio de pago, payer). */
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'EMPLEADO')")
    @PostMapping("/{reservaId}/pago")
    public ResponseEntity<PagoResponse> procesarPago(
            @PathVariable Long reservaId,
            @RequestBody Map<String, Object> formData,
            Authentication authentication) {
        return ResponseEntity.ok(pagoService.procesarPago(reservaId, formData, authentication.getName()));
    }

    /**
     * Webhook de Mercado Pago (público). Acepta los dos formatos que envía MP:
     * Webhooks ({@code ?type=payment&data.id=123} o el mismo dato en el body JSON) e
     * IPN ({@code ?topic=payment&id=123}).
     * Responde 200 a todo lo que no hay que reintentar; un error con MP devuelve 5xx
     * para que MP vuelva a notificar.
     */
    @PostMapping("/notificacion")
    public ResponseEntity<Void> procesarNotificacion(
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "topic", required = false) String topic,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(name = "id", required = false) String id,
            @RequestHeader(name = "x-signature", required = false) String xSignature,
            @RequestHeader(name = "x-request-id", required = false) String xRequestId,
            @RequestBody(required = false) Map<String, Object> body) {

        String tipo = type != null ? type : topic;
        if (tipo == null && body != null && body.get("type") != null) {
            tipo = body.get("type").toString();
        }
        if (!"payment".equals(tipo)) {
            return ResponseEntity.ok().build();
        }

        String paymentId = dataId;
        if (paymentId == null && body != null && body.get("data") instanceof Map<?, ?> data && data.get("id") != null) {
            paymentId = data.get("id").toString();
        }
        if (paymentId == null) {
            paymentId = id;
        }
        if (paymentId == null || paymentId.isBlank()) {
            log.warn("Notificación de MP sin id de pago");
            return ResponseEntity.ok().build();
        }

        // Los webhooks firmados llevan x-signature; las notificaciones IPN no
        if (xSignature != null && !webhookValidator.esValida(xSignature, xRequestId, dataId != null ? dataId : paymentId)) {
            log.warn("Notificación de MP con firma inválida para el pago {}", paymentId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        try {
            pagoService.procesarNotificacion(paymentId);
        } catch (PagoNoEncontradoException e) {
            // 200 para que MP no reintente: el pago no existe (ej. notificación simulada desde el panel)
            log.warn("Notificación de MP ignorada: {}", e.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    /** Empleado/Admin registra un pago en efectivo. */
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    @PostMapping("/{reservaId}/pago-efectivo")
    public ResponseEntity<PagoResponse> pagarEfectivo(
            @PathVariable Long reservaId,
            @Valid @RequestBody PagoManualRequest body,
            Authentication authentication) {
        return ResponseEntity.ok(pagoService.registrarPagoManual(reservaId, body.getMonto(),
                PaymentProvider.MANUAL, body.getJugadorId(), authentication.getName()));
    }

    /**
     * ADMIN: bonifica la reserva (todo o una parte) cuando no se le cobra al cliente. Sin monto,
     * bonifica todo lo que falta. No cuenta como cobrado y no se devuelve si se cancela.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{reservaId}/bonificacion")
    public ResponseEntity<PagoResponse> bonificar(
            @PathVariable Long reservaId,
            @Valid @RequestBody(required = false) BonificacionRequest body,
            Authentication authentication) {
        BonificacionRequest datos = body != null ? body : new BonificacionRequest();
        return ResponseEntity.ok(pagoService.bonificar(reservaId, datos.getMonto(), datos.getMotivo(),
                datos.getJugadorId(), authentication.getName()));
    }

    /** Empleado/Admin registra un pago presencial con tarjeta o QR de Mercado Pago. */
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    @PostMapping("/{reservaId}/pago-presencial")
    public ResponseEntity<PagoResponse> pagarPresencial(
            @PathVariable Long reservaId,
            @Valid @RequestBody PagoManualRequest body,
            Authentication authentication) {
        return ResponseEntity.ok(pagoService.registrarPagoManual(reservaId, body.getMonto(),
                PaymentProvider.MERCADO_PAGO, body.getJugadorId(), authentication.getName()));
    }
}
