package com.LosCiruelos.padel_club_api.pagos.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.pagos.dto.request.CobroQrRequest;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CobroQrResponse;
import com.LosCiruelos.padel_club_api.pagos.service.CobroQrService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Cobro en el club con el QR impreso de la caja de Mercado Pago (ADMIN y EMPLEADO). */
@RestController
@RequestMapping("/admin/reservas/{reservaId}/cobros/qr")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class CobroQrAdminController {

    private final CobroQrService cobroQrService;

    /** Le asigna a la caja el cobro (sin monto, por todo lo que falta). Si ya hay uno activo por el mismo monto, lo devuelve. */
    @PostMapping
    public ResponseEntity<CobroQrResponse> generar(@PathVariable Long reservaId,
            @Valid @RequestBody(required = false) CobroQrRequest body, Authentication authentication) {
        return ResponseEntity.ok(cobroQrService.generar(reservaId, body != null ? body.getMonto() : null,
                authentication.getName()));
    }

    /** Estado del último QR de la reserva (para el modal: se consulta cada pocos segundos). */
    @GetMapping
    public ResponseEntity<CobroQrResponse> estado(@PathVariable Long reservaId, Authentication authentication) {
        return ResponseEntity.ok(cobroQrService.ultimo(reservaId, authentication.getName()));
    }

    /** Cancela el cobro activo y libera la caja (el personal cerró el modal sin que se pagara). */
    @DeleteMapping
    public ResponseEntity<CobroQrResponse> cancelar(@PathVariable Long reservaId, Authentication authentication) {
        return ResponseEntity.ok(cobroQrService.cancelar(reservaId, authentication.getName()));
    }
}
