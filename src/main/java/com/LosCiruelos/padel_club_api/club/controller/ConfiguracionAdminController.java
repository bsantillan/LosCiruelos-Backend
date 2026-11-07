package com.LosCiruelos.padel_club_api.club.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.club.dto.request.ConfiguracionRequest;
import com.LosCiruelos.padel_club_api.club.dto.request.RecordatorioConfigRequest;
import com.LosCiruelos.padel_club_api.club.dto.request.ReservasConfigRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.ConfiguracionResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.RecordatorioConfigResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.ReservasConfigResponse;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Configuración del club que edita el ADMIN: toda junta (precios, seña, duración de los turnos,
 * política de cancelación, recordatorio, anticipación y horarios) o por sección.
 */
@RestController
@RequestMapping("/admin/configuracion")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ConfiguracionAdminController {

    private final ConfiguracionService configuracionService;

    @GetMapping
    public ResponseEntity<ConfiguracionResponse> configuracion() {
        return ResponseEntity.ok(configuracionService.general());
    }

    @PutMapping
    public ResponseEntity<ConfiguracionResponse> actualizar(@Valid @RequestBody ConfiguracionRequest request) {
        return ResponseEntity.ok(configuracionService.actualizarGeneral(request));
    }

    @GetMapping("/avisos")
    public ResponseEntity<RecordatorioConfigResponse> recordatorio() {
        return ResponseEntity.ok(configuracionService.recordatorio());
    }

    @PutMapping("/avisos")
    public ResponseEntity<RecordatorioConfigResponse> actualizarRecordatorio(
            @Valid @RequestBody RecordatorioConfigRequest request) {
        return ResponseEntity.ok(configuracionService.actualizarRecordatorio(request));
    }

    @GetMapping("/reservas")
    public ResponseEntity<ReservasConfigResponse> reservas() {
        return ResponseEntity.ok(configuracionService.reservas());
    }

    @PutMapping("/reservas")
    public ResponseEntity<ReservasConfigResponse> actualizarReservas(@Valid @RequestBody ReservasConfigRequest request) {
        return ResponseEntity.ok(configuracionService.actualizarReservas(request));
    }
}
