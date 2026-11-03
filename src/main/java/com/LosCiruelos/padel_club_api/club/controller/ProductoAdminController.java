package com.LosCiruelos.padel_club_api.club.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.club.dto.request.AjusteStockRequest;
import com.LosCiruelos.padel_club_api.club.dto.request.ProductoRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.ProductoResponse;
import com.LosCiruelos.padel_club_api.club.service.ProductoService;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ProductoDisponibleResponse;
import com.LosCiruelos.padel_club_api.reservas.service.StockService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Sección Stock de productos de la configuración. ADMIN todo; EMPLEADO puede listar y ajustar
 * el stock (reglas en SecurityConfig).
 */
@RestController
@RequestMapping("/admin/configuracion/productos")
@RequiredArgsConstructor
public class ProductoAdminController {

    private final ProductoService productoService;
    private final StockService stockService;

    /**
     * Para el panel (nueva reserva, partidos y turnos fijos): unidades libres de todos los productos
     * activos en un horario, se ofrezcan o no al reservar. {@code excluirReservaId}: al editar, lo que
     * esa reserva ya tiene cuenta como libre.
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    @GetMapping("/disponibilidad")
    public ResponseEntity<List<ProductoDisponibleResponse>> disponibilidad(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaFin,
            @RequestParam(required = false) Long excluirReservaId) {
        return ResponseEntity.ok(stockService.disponibilidadParaPersonal(fecha, horaInicio, horaFin, excluirReservaId));
    }

    @GetMapping
    public ResponseEntity<List<ProductoResponse>> listar() {
        return ResponseEntity.ok(productoService.listarTodos());
    }

    @PostMapping
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productoService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductoResponse> actualizar(@PathVariable Long id,
            @Valid @RequestBody ProductoRequest request) {
        return ResponseEntity.ok(productoService.actualizar(id, request));
    }

    /** Suma o resta unidades: { "ajuste": 12 } para reponer, { "ajuste": -1 } por rotura. */
    @PatchMapping("/{id}/stock")
    public ResponseEntity<ProductoResponse> ajustarStock(@PathVariable Long id,
            @Valid @RequestBody AjusteStockRequest request) {
        return ResponseEntity.ok(productoService.ajustarStock(id, request.getAjuste()));
    }

    /** Baja lógica: deja de ofrecerse en nuevas reservas. */
    @DeleteMapping("/{id}")
    public ResponseEntity<ProductoResponse> desactivar(@PathVariable Long id) {
        return ResponseEntity.ok(productoService.desactivar(id));
    }
}
