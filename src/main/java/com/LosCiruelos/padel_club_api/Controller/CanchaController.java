package com.LosCiruelos.padel_club_api.Controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.DTOs.Responses.CanchaResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.DisponibilidadResponse;
import com.LosCiruelos.padel_club_api.Services.CanchaService;
import com.LosCiruelos.padel_club_api.reservas.service.DisponibilidadService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/canchas")
@RequiredArgsConstructor
public class CanchaController {

    private final CanchaService canchaService;
    private final DisponibilidadService disponibilidadService;

    @GetMapping
    public ResponseEntity<List<CanchaResponse>> listarCanchas() {
        return ResponseEntity.ok(canchaService.listarCanchas());
    }

    @GetMapping("/{id}/disponibilidad")
    public ResponseEntity<DisponibilidadResponse> getDisponibilidad(
            @PathVariable Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) Integer duracion) {
        // duracion es opcional: si no viene, se usa la duración mínima de la configuración
        return ResponseEntity.ok(disponibilidadService.disponibilidad(id, fecha, duracion));
    }
}