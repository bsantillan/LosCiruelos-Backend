package com.LosCiruelos.padel_club_api.club.controller;

import java.util.List;

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

import com.LosCiruelos.padel_club_api.club.dto.request.CanchaRequest;
import com.LosCiruelos.padel_club_api.club.dto.request.OrdenDeFotosRequest;
import com.LosCiruelos.padel_club_api.club.dto.response.BajaDeCanchaResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.CanchaResponse;
import com.LosCiruelos.padel_club_api.club.service.BajaDeCanchas;
import com.LosCiruelos.padel_club_api.club.service.CanchaAdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Gestión de canchas (solo ADMIN). */
@RestController
@RequestMapping("/admin/canchas")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class CanchaAdminController {

    private final CanchaAdminService canchaAdminService;
    private final BajaDeCanchas bajaDeCanchas;

    /** Todas, activas o no, con si se pueden eliminar. */
    @GetMapping
    public ResponseEntity<List<CanchaResponse>> listar() {
        return ResponseEntity.ok(canchaAdminService.listar());
    }

    @PostMapping
    public ResponseEntity<CanchaResponse> crear(@Valid @RequestBody CanchaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(canchaAdminService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CanchaResponse> actualizar(@PathVariable Long id, @Valid @RequestBody CanchaRequest request) {
        return ResponseEntity.ok(canchaAdminService.actualizar(id, request));
    }

    /** Sin confirmar, 409 con las reservas y turnos fijos que tiene; con confirmar=true, los cancela. */
    @PutMapping("/{id}/baja")
    public ResponseEntity<BajaDeCanchaResponse> darDeBaja(@PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean confirmar, Authentication authentication) {
        return ResponseEntity.ok(bajaDeCanchas.darDeBaja(id, confirmar, authentication.getName()));
    }

    @PutMapping("/{id}/reactivacion")
    public ResponseEntity<CanchaResponse> reactivar(@PathVariable Long id) {
        return ResponseEntity.ok(canchaAdminService.reactivar(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        canchaAdminService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CanchaResponse> agregarFoto(@PathVariable Long id, @RequestPart("archivo") MultipartFile archivo) {
        return ResponseEntity.ok(canchaAdminService.agregarFoto(id, archivo));
    }

    @DeleteMapping("/{id}/fotos/{fotoId}")
    public ResponseEntity<CanchaResponse> sacarFoto(@PathVariable Long id, @PathVariable Long fotoId) {
        return ResponseEntity.ok(canchaAdminService.sacarFoto(id, fotoId));
    }

    @PutMapping("/{id}/fotos/orden")
    public ResponseEntity<CanchaResponse> ordenarFotos(@PathVariable Long id,
            @Valid @RequestBody OrdenDeFotosRequest request) {
        return ResponseEntity.ok(canchaAdminService.ordenarFotos(id, request.getFotos()));
    }
}
