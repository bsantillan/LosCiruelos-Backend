package com.LosCiruelos.padel_club_api.reservas.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.reservas.dto.response.ListadoReservasResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.TipoReserva;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaConsultaService;

import lombok.RequiredArgsConstructor;

/** Reservas en el panel del personal del club (ADMIN y EMPLEADO). */
@RestController
@RequestMapping("/admin/reservas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class ReservaAdminController {

    private final ReservaConsultaService reservaConsultaService;

    /**
     * Reservas pasadas y futuras de cualquier cliente, paginadas, con los totales del filtro.
     * Todos los filtros son opcionales y se combinan.
     */
    @GetMapping
    public ResponseEntity<ListadoReservasResponse> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) List<EstadoReserva> estado,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) Long canchaId,
            @RequestParam(required = false) TipoReserva tipo,
            @RequestParam(defaultValue = "false") boolean pendientes,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String orden) {
        return ResponseEntity.ok(reservaConsultaService.listar(desde, hasta, estado, clienteId, cliente, canchaId,
                tipo, pendientes, page, size, orden));
    }
}
