package com.LosCiruelos.padel_club_api.club.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.club.dto.request.CierreRequest;
import com.LosCiruelos.padel_club_api.club.dto.request.ConfiguracionRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.CierreResponse;
import com.LosCiruelos.padel_club_api.club.dto.request.RecordatorioConfigRequest;
import com.LosCiruelos.padel_club_api.club.dto.request.ReservasConfigRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.ConfiguracionResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.RecordatorioConfigResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.ReservasConfigResponse;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionGeneralService;
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
    private final ConfiguracionGeneralService configuracionGeneralService;

    @GetMapping
    public ResponseEntity<ConfiguracionResponse> configuracion() {
        return ResponseEntity.ok(configuracionGeneralService.general());
    }

    /**
     * Si el horario nuevo deja afuera reservas o turnos fijos, 409 con la lista salvo
     * { "confirmar": true }, que guarda y cancela esas reservas con reembolso total.
     */
    @PutMapping
    public ResponseEntity<ConfiguracionResponse> actualizar(@Valid @RequestBody ConfiguracionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(configuracionGeneralService.actualizar(request, authentication.getName()));
    }

    /** Los días cerrados de hoy en adelante. */
    @GetMapping("/cierres")
    public ResponseEntity<List<CierreResponse>> cierres() {
        return ResponseEntity.ok(configuracionGeneralService.cierres());
    }

    /** Cierra el club un día; con reservas ese día, 409 con la lista salvo { "confirmar": true }, que las cancela. */
    @PostMapping("/cierres")
    public ResponseEntity<CierreResponse> cerrar(@Valid @RequestBody CierreRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(configuracionGeneralService.cerrar(request, authentication.getName()));
    }

    /** Vuelve a abrir un día cerrado (las reservas que se cancelaron no vuelven). */
    @DeleteMapping("/cierres/{fecha}")
    public ResponseEntity<Void> reabrir(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        configuracionGeneralService.reabrir(fecha);
        return ResponseEntity.noContent().build();
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
