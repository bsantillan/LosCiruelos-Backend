package com.LosCiruelos.padel_club_api.reservas.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.reservas.dto.response.MetricasResponse;
import com.LosCiruelos.padel_club_api.reservas.service.MetricasService;

import lombok.RequiredArgsConstructor;

/** Solo ADMIN: métricas del club para decidir precios, promociones y retención. */
@RestController
@RequestMapping("/admin/metricas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class MetricasAdminController {

    private final MetricasService metricasService;

    /** Sin fechas: los últimos 30 días. Cada bloque se compara con el período anterior de igual duración. */
    @GetMapping
    public ResponseEntity<MetricasResponse> metricas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {
        return ResponseEntity.ok(metricasService.metricas(desde, hasta, authentication.getName()));
    }
}
