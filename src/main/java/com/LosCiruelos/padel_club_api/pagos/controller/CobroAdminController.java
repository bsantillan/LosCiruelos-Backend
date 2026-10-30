package com.LosCiruelos.padel_club_api.pagos.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.pagos.dto.request.BonificacionRequest;
import com.LosCiruelos.padel_club_api.pagos.dto.request.PagoManualRequest;
import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.service.CobroService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Cobros del personal del club (ADMIN y EMPLEADO) sobre una reserva; bonificar es solo del ADMIN. */
@RestController
@RequestMapping("/admin/reservas/{reservaId}")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class CobroAdminController {

    private final CobroService cobroService;

    /** Pago en efectivo en el club. */
    @PostMapping("/cobros/efectivo")
    public ResponseEntity<PagoResponse> cobrarEfectivo(@PathVariable Long reservaId,
            @Valid @RequestBody PagoManualRequest body, Authentication authentication) {
        return ResponseEntity.ok(cobroService.registrarPagoManual(reservaId, body.getMonto(),
                PaymentProvider.MANUAL, body.getJugadorId(), authentication.getName()));
    }

    /** Pago en el mostrador con tarjeta o QR de Mercado Pago. */
    @PostMapping("/cobros/presencial")
    public ResponseEntity<PagoResponse> cobrarPresencial(@PathVariable Long reservaId,
            @Valid @RequestBody PagoManualRequest body, Authentication authentication) {
        return ResponseEntity.ok(cobroService.registrarPagoManual(reservaId, body.getMonto(),
                PaymentProvider.MERCADO_PAGO, body.getJugadorId(), authentication.getName()));
    }

    /**
     * Bonifica la reserva (todo o una parte) cuando no se le cobra al cliente. Sin monto, bonifica
     * todo lo que falta. No cuenta como cobrado y no se devuelve si se cancela.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/bonificacion")
    public ResponseEntity<PagoResponse> bonificar(@PathVariable Long reservaId,
            @Valid @RequestBody(required = false) BonificacionRequest body, Authentication authentication) {
        BonificacionRequest datos = body != null ? body : new BonificacionRequest();
        return ResponseEntity.ok(cobroService.bonificar(reservaId, datos.getMonto(), datos.getMotivo(),
                datos.getJugadorId(), authentication.getName()));
    }
}
