package com.LosCiruelos.padel_club_api.club.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.LosCiruelos.padel_club_api.club.dto.request.NovedadRequest;
import com.LosCiruelos.padel_club_api.club.dto.request.PublicacionRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.NovedadResponse;
import com.LosCiruelos.padel_club_api.club.service.NovedadService;
import com.LosCiruelos.padel_club_api.common.PaginaResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Las novedades del club (solo ADMIN). */
@RestController
@RequestMapping("/admin/novedades")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class NovedadAdminController {

    private final NovedadService novedadService;

    @GetMapping
    public ResponseEntity<PaginaResponse<NovedadResponse>> listar(@RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(novedadService.listar(page, size));
    }

    @PostMapping
    public ResponseEntity<NovedadResponse> crear(@Valid @RequestBody NovedadRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(novedadService.crear(request, authentication.getName()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<NovedadResponse> actualizar(@PathVariable Long id, @Valid @RequestBody NovedadRequest request) {
        return ResponseEntity.ok(novedadService.actualizar(id, request));
    }

    @PutMapping("/{id}/publicacion")
    public ResponseEntity<NovedadResponse> publicar(@PathVariable Long id, @Valid @RequestBody PublicacionRequest body) {
        return ResponseEntity.ok(novedadService.publicar(id, body.getPublicada()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        novedadService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/imagen", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NovedadResponse> ponerImagen(@PathVariable Long id, @RequestPart("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(novedadService.ponerImagen(id, archivo));
    }

    @DeleteMapping("/{id}/imagen")
    public ResponseEntity<NovedadResponse> sacarImagen(@PathVariable Long id) {
        return ResponseEntity.ok(novedadService.sacarImagen(id));
    }
}
