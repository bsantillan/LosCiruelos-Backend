package com.LosCiruelos.padel_club_api.reservas.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.common.PaginaResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.request.CrearReservaRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.service.CancelacionService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaConsultaService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Reservas del cliente: las propias y los partidos en los que juega. El cliente es siempre el
 * usuario autenticado. Lo del personal del club está en {@link ReservaAdminController}.
 */
@RestController
@RequestMapping("/reservas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('CLIENTE')")
public class ReservaController {

    private final ReservaService reservaService;
    private final ReservaConsultaService reservaConsultaService;
    private final CancelacionService cancelacionService;

    @PostMapping
    public ResponseEntity<ReservaResponse> crearReserva(
            @Valid @RequestBody CrearReservaRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservaService.crearParaMi(request, authentication.getName()));
    }

    /**
     * Reservas del usuario autenticado, paginadas. Filtros opcionales: tipo (proximas | historial),
     * estado (lista separada por coma), desde / hasta (fecha del turno), page (desde 0) y size (1 a 50).
     * Las canceladas y vencidas sin ningún pago que haya movido dinero no se muestran (tampoco filtrando
     * por estado), salvo con incluirDescartadas=true.
     */
    @GetMapping("/mias")
    public ResponseEntity<PaginaResponse<ReservaResponse>> misReservas(
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) List<EstadoReserva> estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "false") boolean incluirDescartadas,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication) {
        return ResponseEntity.ok(reservaConsultaService.misReservas(authentication.getName(), tipo, estado, desde, hasta,
                incluirDescartadas, page, size));
    }

    /** Detalle con sus pagos (el front lo consulta para ver el resultado de un pago en proceso). */
    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponse> obtenerReserva(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(reservaConsultaService.obtener(id, authentication.getName()));
    }

    @PostMapping("/{id}/expirar")
    public ResponseEntity<Void> expirarReserva(@PathVariable Long id, Authentication authentication) {
        reservaService.expirarPorUsuario(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    /** Cancela la reserva y hace los reembolsos que correspondan según la política de cancelación. */
    @DeleteMapping("/{id}/cancelar")
    public ResponseEntity<ReservaResponse> cancelarReserva(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(cancelacionService.cancelarComoCliente(id, authentication.getName()));
    }
}
