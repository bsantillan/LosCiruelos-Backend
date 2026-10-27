package com.LosCiruelos.padel_club_api.reservas.controller;

import java.util.List;

import java.time.LocalDate;

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

import com.LosCiruelos.padel_club_api.reservas.dto.request.CrearReservaRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.request.DevolucionRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PaginaResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.pagos.service.CobroService;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.OpcionReembolso;
import com.LosCiruelos.padel_club_api.reservas.service.CancelacionService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaConsultaService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/reservas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'EMPLEADO')")
public class ReservaController {

    private final ReservaService reservaService;
    private final ReservaConsultaService reservaConsultaService;
    private final CancelacionService cancelacionService;
    private final CobroService cobroService;

    @PostMapping
    public ResponseEntity<ReservaResponse> crearReserva(
            @Valid @RequestBody CrearReservaRequest request,
            Authentication authentication) {
        // con "bonificacion" (solo ADMIN) se crea y se bonifica en la misma transacción
        ReservaResponse creada = request.getBonificacion() != null
                ? cobroService.crearReservaBonificada(request, authentication.getName())
                : reservaService.crearReserva(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
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

    /**
     * Personal del club: turnos ya jugados que deben saldo (señados o parciales), del más viejo al más nuevo.
     * Pendiente para la rama de feature: pasa a /admin/reservas.
     */
    @GetMapping("/saldos-pendientes")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<List<ReservaResponse>> saldosPendientes() {
        return ResponseEntity.ok(reservaConsultaService.saldosPendientes());
    }

    /** Detalle con sus pagos (el front lo consulta para ver el resultado de un pago en proceso). */
    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponse> obtenerReserva(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(reservaConsultaService.obtener(id, authentication.getName()));
    }

    /**
     * Personal del club: reservas cuyo turno empezó y tienen paletas sin devolver.
     * Pendiente para la rama de feature: pasa a /admin/reservas.
     */
    @GetMapping("/devoluciones-pendientes")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<List<ReservaResponse>> devolucionesPendientes() {
        return ResponseEntity.ok(reservaConsultaService.devolucionesPendientes());
    }

    /**
     * Personal del club: registra cuántas paletas devolvieron (las faltantes bajan el stock).
     * Pendiente para la rama de feature: pasa a /admin/reservas/{id}/devolucion.
     */
    @PostMapping("/{id}/devolucion")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<ReservaResponse> registrarDevolucion(
            @PathVariable Long id,
            @Valid @RequestBody DevolucionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(reservaService.registrarDevolucion(id, request.getPaletasDevueltas(),
                authentication.getName()));
    }

    @PostMapping("/{id}/expirar")
    public ResponseEntity<Void> expirarReserva(@PathVariable Long id, Authentication authentication) {
        reservaService.expirarPorUsuario(id, authentication.getName());
        return ResponseEntity.ok().build();
    }

    /**
     * Cancela la reserva y hace los reembolsos que correspondan. El cliente no elige el reembolso
     * (se aplica la política). ADMIN y EMPLEADO lo indican con {@code reembolso=TOTAL|SENIA|NADA}.
     */
    @DeleteMapping("/{id}/cancelar")
    public ResponseEntity<ReservaResponse> cancelarReserva(
            @PathVariable Long id,
            @RequestParam(required = false) OpcionReembolso reembolso,
            Authentication authentication) {
        return ResponseEntity.ok(cancelacionService.cancelar(id, authentication.getName(), reembolso));
    }
}
