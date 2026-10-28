package com.LosCiruelos.padel_club_api.reportes.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.reportes.dto.response.ResumenResponse;
import com.LosCiruelos.padel_club_api.reportes.service.ResumenService;

import lombok.RequiredArgsConstructor;

/**
 * Resumen del período para el panel del club (montos, partidos y productos). El ADMIN ve cualquier
 * período; el EMPLEADO, solo el día de hoy. Sin fechas, el resumen es de hoy.
 */
@RestController
@RequestMapping("/admin/resumen")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class ResumenController {

    private final ResumenService resumenService;

    @GetMapping
    public ResponseEntity<ResumenResponse> resumen(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {
        return ResponseEntity.ok(resumenService.resumen(desde, hasta, authentication.getName()));
    }
}
