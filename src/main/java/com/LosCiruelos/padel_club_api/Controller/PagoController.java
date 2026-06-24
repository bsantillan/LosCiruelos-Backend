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
@RequestMapping("/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    @PreAuthorize("hasAnyRole('CLIENTE')") 
    @PostMapping("/{reserva_id}/iniciar")
    public ResponseEntity<PagoResponse> iniciarPago(
            @PathVariable Long reserva_id,
            @RequestParam Boolean pagarTotal) {
        return ResponseEntity.ok(pagoService.iniciarPago(reserva_id, pagarTotal));
    }

    @PreAuthorize("hasAnyRole('CLIENTE')")
    @PostMapping("/{reserva_id}/pago")
    public ResponseEntity<PagoResponse> procesarPago(
            @PathVariable Long reserva_id,
            @RequestBody Map<String, Object> formData) {
        return ResponseEntity.ok(pagoService.procesarPago(reserva_id, formData));
    }

    @PostMapping("/notificacion")
    public ResponseEntity<Void> procesarNotificacion(
            @RequestParam String topic,
            @RequestParam String payment_id) {
        pagoService.procesarNotificacion(payment_id, topic);
        return ResponseEntity.ok().build();
    }

    // Empleado/Admin registra pago en efectivo
    @PostMapping("/{reserva_id}/pago-efectivo")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<PagoResponse> pagarEfectivo(
            @PathVariable Long reserva_id,
            @RequestBody Map<String, BigDecimal> body) {
        return ResponseEntity.ok(pagoService.registrarPagoManual(reserva_id, body.get("monto"), PaymentProvider.MANUAL));
    }

    // Empleado/Admin registra pago presencial con tarjeta/QR
    @PostMapping("/{id}/pago-presencial")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<PagoResponse> pagarPresencial(
            @PathVariable Long id,
            @RequestBody Map<String, BigDecimal> body) {
        return ResponseEntity.ok(pagoService.registrarPagoManual(id, body.get("monto"), PaymentProvider.MERCADO_PAGO));
    }
}