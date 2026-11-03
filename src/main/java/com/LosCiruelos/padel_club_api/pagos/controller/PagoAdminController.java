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

import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.pagos.service.PagoConsultaService;

import lombok.RequiredArgsConstructor;

/** Pagos para controlar contra las cuentas del club (solo ADMIN). */
@RestController
@RequestMapping("/admin/pagos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PagoAdminController {

    private final PagoConsultaService pagoConsultaService;

    /** Pagos con Cuenta DNI del período, para verificarlos; {@code verificado=false} trae los pendientes. */
    @GetMapping("/cuenta-dni")
    public ResponseEntity<List<PagoResponse>> cuentaDni(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Boolean verificado) {
        return ResponseEntity.ok(pagoConsultaService.cuentaDni(desde, hasta, verificado));
    }
}
