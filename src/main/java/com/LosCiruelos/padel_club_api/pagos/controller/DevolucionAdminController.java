package com.LosCiruelos.padel_club_api.pagos.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.pagos.dto.request.AnulacionRequest;
import com.LosCiruelos.padel_club_api.pagos.dto.request.DevolucionRequest;
import com.LosCiruelos.padel_club_api.pagos.dto.request.RevisionRequest;
import com.LosCiruelos.padel_club_api.pagos.dto.response.DevolucionListadoResponse;
import com.LosCiruelos.padel_club_api.pagos.service.AnulacionService;
import com.LosCiruelos.padel_club_api.pagos.service.DevolucionService;
import com.LosCiruelos.padel_club_api.pagos.service.PagoConsultaService;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Deshacer cobros de una reserva: anular uno cargado por error y devolverle plata al cliente por
 * línea (ADMIN, y EMPLEADO en su jornada), entregar una devolución pendiente y, solo el ADMIN, revisar
 * lo que devolvió el EMPLEADO.
 */
@RestController
@RequestMapping("/admin/reservas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class DevolucionAdminController {

    private final AnulacionService anulacionService;
    private final DevolucionService devolucionService;
    private final PagoConsultaService pagoConsultaService;

    @PostMapping("/{reservaId}/pagos/{pagoId}/anulacion")
    public ResponseEntity<ReservaResponse> anular(@PathVariable Long reservaId, @PathVariable Long pagoId,
            @Valid @RequestBody AnulacionRequest body, Authentication authentication) {
        return ResponseEntity.ok(anulacionService.anular(reservaId, pagoId, body.getMotivo(), authentication.getName()));
    }

    @PostMapping("/{reservaId}/pagos/{pagoId}/devoluciones")
    public ResponseEntity<ReservaResponse> devolver(@PathVariable Long reservaId, @PathVariable Long pagoId,
            @Valid @RequestBody DevolucionRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(devolucionService.devolver(reservaId, pagoId, body, authentication.getName()));
    }

    @PutMapping("/{reservaId}/devoluciones/{devolucionId}/entrega")
    public ResponseEntity<ReservaResponse> entregar(@PathVariable Long reservaId, @PathVariable Long devolucionId,
            Authentication authentication) {
        return ResponseEntity.ok(devolucionService.entregar(reservaId, devolucionId, authentication.getName()));
    }

    /** Solo ADMIN: marca (o desmarca) como revisada una devolución del EMPLEADO. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{reservaId}/devoluciones/{devolucionId}/revision")
    public ResponseEntity<ReservaResponse> revisar(@PathVariable Long reservaId, @PathVariable Long devolucionId,
            @Valid @RequestBody RevisionRequest body, Authentication authentication) {
        return ResponseEntity.ok(devolucionService.revisar(reservaId, devolucionId, body.getRevisada(),
                authentication.getName()));
    }

    /** Lo que hay que devolverle a mano a cada cliente. El EMPLEADO ve las de la jornada. */
    @GetMapping("/devoluciones-a-entregar")
    public ResponseEntity<List<DevolucionListadoResponse>> aEntregar(Authentication authentication) {
        return ResponseEntity.ok(pagoConsultaService.devolucionesAEntregar(authentication.getName()));
    }

    /** Solo ADMIN: lo que devolvió el EMPLEADO y todavía no se revisó. */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/devoluciones-sin-revisar")
    public ResponseEntity<List<DevolucionListadoResponse>> sinRevisar(Authentication authentication) {
        return ResponseEntity.ok(pagoConsultaService.devolucionesSinRevisar(authentication.getName()));
    }
}
