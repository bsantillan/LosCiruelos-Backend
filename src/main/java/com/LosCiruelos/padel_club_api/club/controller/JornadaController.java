package com.LosCiruelos.padel_club_api.club.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.club.dto.response.JornadaResponse;
import com.LosCiruelos.padel_club_api.club.service.JornadaService;
import com.LosCiruelos.padel_club_api.mapper.ConfiguracionMapper;

import lombok.RequiredArgsConstructor;

/** La jornada de trabajo en curso, para que el front sepa qué fechas mostrarle al EMPLEADO. */
@RestController
@RequestMapping("/admin/jornada")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class JornadaController {

    private final JornadaService jornadaService;
    private final ConfiguracionMapper configuracionMapper;

    @GetMapping
    public ResponseEntity<JornadaResponse> actual() {
        return ResponseEntity.ok(configuracionMapper.aJornada(jornadaService.actual()));
    }
}
