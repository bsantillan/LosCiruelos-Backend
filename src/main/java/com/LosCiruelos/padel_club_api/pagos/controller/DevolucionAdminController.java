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
import com.LosCiruelos.padel_club_api.pagos.dto.response.DevolucionAEntregarResponse;
import com.LosCiruelos.padel_club_api.pagos.service.AnulacionService;
import com.LosCiruelos.padel_club_api.pagos.service.DevolucionService;
import com.LosCiruelos.padel_club_api.pagos.service.PagoConsultaService;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Deshacer cobros de una reserva: anular uno cargado por error (ADMIN y EMPLEADO en su jornada),
 * devolverle plata al cliente por línea (solo ADMIN) y entregar una devolución pendiente.
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

    @PreAuthorize("hasRole('ADMIN')")
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

    /** Lo que hay que devolverle a mano a cada cliente. El EMPLEADO ve las de la jornada. */
    @GetMapping("/devoluciones-a-entregar")
    public ResponseEntity<List<DevolucionAEntregarResponse>> aEntregar(Authentication authentication) {
        return ResponseEntity.ok(pagoConsultaService.devolucionesAEntregar(authentication.getName()));
    }
}
