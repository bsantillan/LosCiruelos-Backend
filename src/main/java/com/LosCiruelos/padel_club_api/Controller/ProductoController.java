package com.LosCiruelos.padel_club_api.Controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.DTOs.Responses.ProductoDisponibleResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ProductoResponse;
import com.LosCiruelos.padel_club_api.Services.ProductoService;

import lombok.RequiredArgsConstructor;

/** Productos que se pueden pedir con una reserva (precios y disponibilidad). */
@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;

    @GetMapping
    public ResponseEntity<List<ProductoResponse>> listar() {
        return ResponseEntity.ok(productoService.listarActivos());
    }

    /** Unidades disponibles de cada producto para un horario (para limitar los selectores del front). */
    @GetMapping("/disponibilidad")
    public ResponseEntity<List<ProductoDisponibleResponse>> disponibilidad(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime horaFin) {
        return ResponseEntity.ok(productoService.disponibilidad(fecha, horaInicio, horaFin));
    }
}
