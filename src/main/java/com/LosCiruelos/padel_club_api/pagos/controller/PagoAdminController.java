package com.LosCiruelos.padel_club_api.pagos.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.pagos.dto.response.CobroCuentaDniResponse;
import com.LosCiruelos.padel_club_api.pagos.service.PagoConsultaService;

import lombok.RequiredArgsConstructor;

/** Pagos para controlar contra las cuentas del club (solo ADMIN). */
@RestController
@RequestMapping("/admin/pagos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PagoAdminController {

    private final PagoConsultaService pagoConsultaService;

    /**
     * Lo cobrado con Cuenta DNI en el período (reservas y ventas sueltas), para verificarlo;
     * {@code verificado=false} trae los pendientes.
     */
    @GetMapping("/cuenta-dni")
    public ResponseEntity<List<CobroCuentaDniResponse>> cuentaDni(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Boolean verificado) {
        return ResponseEntity.ok(pagoConsultaService.cuentaDni(desde, hasta, verificado));
    }

    /**
     * Todo lo cobrado a mano que se controla contra la cuenta: Cuenta DNI y transferencias de Mercado
     * Pago (reservas y ventas sueltas). {@code medio} (repetible) filtra uno; sin indicar, los dos.
     */
    @GetMapping("/verificables")
    public ResponseEntity<List<CobroCuentaDniResponse>> verificables(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Boolean verificado,
            @RequestParam(required = false) List<com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago> medio) {
        return ResponseEntity.ok(pagoConsultaService.verificables(desde, hasta, verificado, medio));
    }
}
