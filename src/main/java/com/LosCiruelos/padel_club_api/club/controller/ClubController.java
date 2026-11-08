package com.LosCiruelos.padel_club_api.club.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.club.dto.response.HorariosClubResponse;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionGeneralService;

import lombok.RequiredArgsConstructor;

/** Datos públicos del club (sin sesión): para la grilla del panel y el sitio del cliente. */
@RestController
@RequestMapping("/club")
@RequiredArgsConstructor
public class ClubController {

    private final ConfiguracionGeneralService configuracionGeneralService;

    /** Días y horarios de apertura de lunes a domingo, y la duración mínima y máxima de los turnos. */
    @GetMapping("/horarios")
    public ResponseEntity<HorariosClubResponse> horarios() {
        return ResponseEntity.ok(configuracionGeneralService.horarios());
    }
}
