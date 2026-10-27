package com.LosCiruelos.padel_club_api.club.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.club.dto.request.AjusteStockRequest;
import com.LosCiruelos.padel_club_api.club.dto.request.ProductoRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.ProductoResponse;
import com.LosCiruelos.padel_club_api.club.service.ProductoService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Sección Stock de productos de la configuración. ADMIN todo; EMPLEADO puede listar y ajustar
 * el stock (reglas en SecurityConfig).
 */
@RestController
@RequestMapping("/admin/configuracion/productos")
@RequiredArgsConstructor
public class AdminProductoController {

    private final ProductoService productoService;

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
