package com.LosCiruelos.padel_club_api.club.controller;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.reservas.dto.response.ProductoDisponibleResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.ProductoResponse;
import com.LosCiruelos.padel_club_api.club.service.ProductoService;
import com.LosCiruelos.padel_club_api.reservas.service.StockService;

import lombok.RequiredArgsConstructor;

/** Productos que se pueden pedir con una reserva (precios y disponibilidad). */
@RestController
@RequestMapping("/productos")
@RequiredArgsConstructor
public class ProductoController {

    private final ProductoService productoService;
    private final StockService stockService;

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
        return ResponseEntity.ok(stockService.disponibilidad(fecha, horaInicio, horaFin));
    }
}
