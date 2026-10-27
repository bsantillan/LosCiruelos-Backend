package com.LosCiruelos.padel_club_api.pagos.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.pagos.service.PagoOnlineService;

import lombok.RequiredArgsConstructor;

/**
 * Pagos online del cliente con Mercado Pago (Brick / Wallet) para sus reservas. Los cobros del
 * personal están en {@link CobroAdminController} y el webhook de MP en {@link MercadoPagoWebhookController}.
 */
@RestController
@RequestMapping("/pagos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENTE')")
public class PagoController {

    private final PagoOnlineService pagoOnlineService;

    /** Crea el pago (seña o total) y la preferencia de MP. Devuelve el preference_id en paymentToken. */
    @PostMapping("/{reservaId}/iniciar")
    public ResponseEntity<PagoResponse> iniciarPago(
            @PathVariable Long reservaId,
            @RequestParam(defaultValue = "false") Boolean pagarTotal,
            Authentication authentication) {
        return ResponseEntity.ok(pagoOnlineService.iniciarPago(reservaId, pagarTotal, authentication.getName()));
    }

    /** Cobra con el formData del Payment Brick (token de tarjeta, cuotas, medio de pago, payer). */
    @PostMapping("/{reservaId}/pago")
    public ResponseEntity<PagoResponse> procesarPago(
            @PathVariable Long reservaId,
            @RequestBody Map<String, Object> formData,
            Authentication authentication) {
        return ResponseEntity.ok(pagoOnlineService.procesarPago(reservaId, formData, authentication.getName()));
    }
}
