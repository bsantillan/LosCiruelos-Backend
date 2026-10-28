package com.LosCiruelos.padel_club_api.club.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.club.dto.request.RecordatorioConfigRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.RecordatorioConfigResponse;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Configuración del club que edita el ADMIN: el recordatorio del turno. */
@RestController
@RequestMapping("/admin/configuracion")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ConfiguracionAdminController {

    private final ConfiguracionService configuracionService;

    @GetMapping("/avisos")
    public ResponseEntity<RecordatorioConfigResponse> recordatorio() {
        return ResponseEntity.ok(configuracionService.recordatorio());
    }

    @PutMapping("/avisos")
    public ResponseEntity<RecordatorioConfigResponse> actualizarRecordatorio(
            @Valid @RequestBody RecordatorioConfigRequest request) {
        return ResponseEntity.ok(configuracionService.actualizarRecordatorio(request));
    }
}
