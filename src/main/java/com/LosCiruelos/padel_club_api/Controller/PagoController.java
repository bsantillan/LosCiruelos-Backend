package com.LosCiruelos.padel_club_api.Controller;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.DTOs.Responses.PagoResponse;
import com.LosCiruelos.padel_club_api.Entities.Enum.PaymentProvider;
import com.LosCiruelos.padel_club_api.Services.PagoService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/reservas")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    @PreAuthorize("hasAnyRole('CLIENTE')")
    @PostMapping("/{id}/pagar")
    public ResponseEntity<PagoResponse> iniciarPago(
            @PathVariable Long id,
            @RequestParam Boolean pagarTotal) {
        return ResponseEntity.ok(pagoService.iniciarPago(id, pagarTotal));
    }

    // Webhook de MP — notifica resultado del pago online
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestParam String topic,
            @RequestParam String id) {
        pagoService.procesarWebhook(id, topic);
        return ResponseEntity.ok().build();
    }

    // Empleado/Admin registra pago en efectivo
    @PostMapping("/{id}/pagar-efectivo")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<PagoResponse> pagarEfectivo(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody Map<String, BigDecimal> body) {
        return ResponseEntity.ok(pagoService.registrarPagoManual(id, body.get("monto"), PaymentProvider.MANUAL));
    }

    // Empleado/Admin registra pago presencial con tarjeta/QR
    @PostMapping("/{id}/pagar-presencial")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<PagoResponse> pagarPresencial(
            @PathVariable Long id,
            @RequestBody Map<String, BigDecimal> body) {
        return ResponseEntity.ok(pagoService.registrarPagoManual(id, body.get("monto"), PaymentProvider.MERCADO_PAGO));
    }
}