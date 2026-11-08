package com.LosCiruelos.padel_club_api.ventas.controller;

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

import com.LosCiruelos.padel_club_api.ventas.dto.request.AnulacionVentaRequest;
import com.LosCiruelos.padel_club_api.ventas.dto.request.ClienteVentaRequest;
import com.LosCiruelos.padel_club_api.ventas.dto.request.DevolucionVentaRequest;
import com.LosCiruelos.padel_club_api.ventas.dto.request.VentaRequest;
import com.LosCiruelos.padel_club_api.ventas.dto.request.VerificacionVentaRequest;
import com.LosCiruelos.padel_club_api.ventas.dto.response.MedioDeVentaResponse;
import com.LosCiruelos.padel_club_api.ventas.dto.response.VentaResponse;
import com.LosCiruelos.padel_club_api.ventas.service.VentaConsultaService;
import com.LosCiruelos.padel_club_api.ventas.service.VentaQrService;
import com.LosCiruelos.padel_club_api.ventas.service.VentaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Ventas sueltas del club (sin reserva, partido ni turno): las registra y las consulta el personal. */
@RestController
@RequestMapping("/admin/ventas")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class VentaAdminController {

    private final VentaService ventaService;
    private final VentaConsultaService ventaConsultaService;
    private final VentaQrService ventaQrService;

    /** Registra una venta: en efectivo o con Cuenta DNI queda cobrada; con Mercado Pago, esperando el QR de la caja. */
    @PostMapping
    public ResponseEntity<VentaResponse> registrar(@Valid @RequestBody VentaRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ventaService.registrar(request, authentication.getName()));
    }

    /** Solo ADMIN: marca (o desmarca) una venta con Cuenta DNI como verificada en la cuenta del club. */
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/{id}/verificacion")
    public ResponseEntity<VentaResponse> verificar(@PathVariable Long id,
            @Valid @RequestBody VerificacionVentaRequest body, Authentication authentication) {
        return ResponseEntity.ok(ventaService.verificar(id, body.getVerificada(), authentication.getName()));
    }

    /** Deja la venta sin efecto, con motivo. El EMPLEADO solo anula las de hoy; el ADMIN, cualquiera. */
    @PostMapping("/{id}/anulacion")
    public ResponseEntity<VentaResponse> anular(@PathVariable Long id, @Valid @RequestBody AnulacionVentaRequest body,
            Authentication authentication) {
        return ResponseEntity.ok(ventaService.anular(id, body.getMotivo(), authentication.getName()));
    }

    /** Solo ADMIN: el cliente devuelve todo o parte de lo que compró (cuenta en la caja de hoy). */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/devoluciones")
    public ResponseEntity<VentaResponse> devolver(@PathVariable Long id, @Valid @RequestBody DevolucionVentaRequest body,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ventaService.devolver(id, body, authentication.getName()));
    }

    /** Cambia el cliente asociado ({ "clienteId": null } la deja como venta al público). */
    @PutMapping("/{id}/cliente")
    public ResponseEntity<VentaResponse> cambiarCliente(@PathVariable Long id, @RequestBody ClienteVentaRequest body,
            Authentication authentication) {
        return ResponseEntity.ok(ventaService.cambiarCliente(id, body.getClienteId(), authentication.getName()));
    }

    /** El personal cerró el modal del QR sin que se pagara: la venta queda NO_COBRADA. */
    @DeleteMapping("/{id}/qr")
    public ResponseEntity<VentaResponse> cancelarQr(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ventaQrService.cancelar(id, authentication.getName()));
    }

    /** Los medios con que se puede cobrar una venta (Mercado Pago, solo si el QR de la caja está configurado). */
    @GetMapping("/medios")
    public ResponseEntity<List<MedioDeVentaResponse>> medios() {
        return ResponseEntity.ok(ventaConsultaService.medios());
    }

    /** Una venta (el EMPLEADO, solo las de hoy); el modal del QR la consulta para ver si se pagó. */
    @GetMapping("/{id}")
    public ResponseEntity<VentaResponse> obtener(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(ventaConsultaService.obtener(id, authentication.getName()));
    }

    /** Ventas del período (sin fechas, las de hoy). El EMPLEADO ve solo las de hoy. */
    @GetMapping
    public ResponseEntity<List<VentaResponse>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication) {
        return ResponseEntity.ok(ventaConsultaService.listar(desde, hasta, authentication.getName()));
    }
}
