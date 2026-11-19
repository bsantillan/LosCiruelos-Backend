package com.LosCiruelos.padel_club_api.club.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.club.dto.response.NovedadPublicaResponse;
import com.LosCiruelos.padel_club_api.club.service.NovedadService;

import lombok.RequiredArgsConstructor;

/** Las novedades publicadas (público, sin sesión): la página de inicio. */
@RestController
@RequestMapping("/novedades")
@RequiredArgsConstructor
public class NovedadController {

    private final NovedadService novedadService;

    @GetMapping
    public ResponseEntity<List<NovedadPublicaResponse>> vigentes(@RequestParam(required = false) Integer limite) {
        return ResponseEntity.ok(novedadService.vigentes(limite));
    }
}
