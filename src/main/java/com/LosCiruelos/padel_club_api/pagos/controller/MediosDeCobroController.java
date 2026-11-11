package com.LosCiruelos.padel_club_api.pagos.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.pagos.dto.response.MedioDeCobroResponse;
import com.LosCiruelos.padel_club_api.pagos.service.PagoConsultaService;

import lombok.RequiredArgsConstructor;

/** Los medios con que el personal cobra una reserva en el club (para el modal de cobro). */
@RestController
@RequestMapping("/admin/reservas/medios-de-cobro")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class MediosDeCobroController {

    private final PagoConsultaService pagoConsultaService;

    @GetMapping
    public ResponseEntity<List<MedioDeCobroResponse>> medios() {
        return ResponseEntity.ok(pagoConsultaService.mediosDeCobro());
    }
}
