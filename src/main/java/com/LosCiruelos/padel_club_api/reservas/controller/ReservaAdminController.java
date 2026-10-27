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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.pagos.service.CobroService;
import com.LosCiruelos.padel_club_api.reservas.dto.request.CrearReservaAdminRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.request.DevolucionRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.request.PartidoRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ListadoReservasResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.OpcionReembolso;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.TipoReserva;
import com.LosCiruelos.padel_club_api.reservas.service.CancelacionService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaConsultaService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Reservas en el panel del personal del club (ADMIN y EMPLEADO): cualquier reserva de cualquier
 * cliente, normales y partidos. Armar y modificar partidos es solo del ADMIN (por método); cancelar
 * un partido también, y eso lo controla el service.
 */
@RestController
@RequestMapping("/admin/reservas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class ReservaAdminController {

    private final ReservaService reservaService;
    private final ReservaConsultaService reservaConsultaService;
    private final CancelacionService cancelacionService;
    private final CobroService cobroService;

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

    /** Reserva para un cliente (clienteId obligatorio). Con "bonificacion" (solo ADMIN) se crea y se bonifica junto. */
    @PostMapping
    public ResponseEntity<ReservaResponse> crear(@Valid @RequestBody CrearReservaAdminRequest request,
            Authentication authentication) {
        ReservaResponse creada = request.getBonificacion() != null
                ? cobroService.crearReservaBonificada(request, authentication.getName())
                : reservaService.crearParaCliente(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creada);
    }

    /**
     * Solo ADMIN: arma un partido de 4 jugadores (clientes registrados o invitados). Se consulta,
     * se cobra y se cancela como cualquier reserva.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/partidos")
    public ResponseEntity<ReservaResponse> crearPartido(@Valid @RequestBody PartidoRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservaService.crearPartido(request, authentication.getName()));
    }

    /** Solo ADMIN: se manda el partido completo (cancha, fecha, horario, los 4 jugadores y productos). */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/partidos/{id}")
    public ResponseEntity<ReservaResponse> actualizarPartido(@PathVariable Long id,
            @Valid @RequestBody PartidoRequest request, Authentication authentication) {
        return ResponseEntity.ok(reservaService.actualizarPartido(id, request, authentication.getName()));
    }

    /** Detalle de cualquier reserva, con sus pagos. */
    @GetMapping("/{id}")
    public ResponseEntity<ReservaResponse> obtener(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(reservaConsultaService.obtener(id, authentication.getName()));
    }

    /**
     * Cancela la reserva. Si tiene dinero cobrado hay que indicar qué se devuelve:
     * {@code reembolso=TOTAL|SENIA|NADA}. Un partido lo cancela solo el ADMIN.
     */
    @DeleteMapping("/{id}/cancelar")
    public ResponseEntity<ReservaResponse> cancelar(@PathVariable Long id,
            @RequestParam(required = false) OpcionReembolso reembolso, Authentication authentication) {
        return ResponseEntity.ok(cancelacionService.cancelarComoPersonal(id, reembolso, authentication.getName()));
    }

    /** Turnos ya jugados que deben saldo (señados o parciales), del más viejo al más nuevo. */
    @GetMapping("/saldos-pendientes")
    public ResponseEntity<List<ReservaResponse>> saldosPendientes() {
        return ResponseEntity.ok(reservaConsultaService.saldosPendientes());
    }

    /** Reservas cuyo turno empezó y tienen paletas sin devolver. */
    @GetMapping("/devoluciones-pendientes")
    public ResponseEntity<List<ReservaResponse>> devolucionesPendientes() {
        return ResponseEntity.ok(reservaConsultaService.devolucionesPendientes());
    }

    /** Registra cuántas paletas devolvieron (las faltantes bajan el stock). */
    @PostMapping("/{id}/devolucion")
    public ResponseEntity<ReservaResponse> registrarDevolucion(@PathVariable Long id,
            @Valid @RequestBody DevolucionRequest request, Authentication authentication) {
        return ResponseEntity.ok(reservaService.registrarDevolucion(id, request.getPaletasDevueltas(),
                authentication.getName()));
    }
}
