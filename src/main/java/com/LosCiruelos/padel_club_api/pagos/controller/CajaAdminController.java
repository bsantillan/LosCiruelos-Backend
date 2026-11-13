package com.LosCiruelos.padel_club_api.pagos.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.pagos.dto.response.CajaResponse;
import com.LosCiruelos.padel_club_api.pagos.service.CajaService;

import lombok.RequiredArgsConstructor;

/** El cierre de caja de una jornada: ADMIN cualquiera, EMPLEADO la que está en curso. */
@RestController
@RequestMapping("/admin/caja")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class CajaAdminController {

    private final CajaService cajaService;

    @GetMapping
    public ResponseEntity<CajaResponse> cierre(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            Authentication authentication) {
        return ResponseEntity.ok(cajaService.cierre(fecha, authentication.getName()));
    }
}
