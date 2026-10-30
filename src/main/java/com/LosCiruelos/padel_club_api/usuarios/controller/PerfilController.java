package com.LosCiruelos.padel_club_api.usuarios.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.usuarios.dto.request.PerfilRequest;
import com.LosCiruelos.padel_club_api.usuarios.dto.response.PerfilResponse;
import com.LosCiruelos.padel_club_api.usuarios.service.PerfilService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/perfil")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilService perfilService;

    @GetMapping
    public ResponseEntity<PerfilResponse> getPerfil(Authentication authentication) {
        return ResponseEntity.ok(perfilService.getPerfil(authentication.getName()));
    }

    @PutMapping
    public ResponseEntity<PerfilResponse> updatePerfil(
            @Valid @RequestBody PerfilRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(perfilService.updatePerfil(request, authentication.getName()));
    }

    /** Desactiva la cuenta y cierra todas las sesiones (el body con refreshToken ya no es necesario). */
    @DeleteMapping("/desactivar")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<Map<String, String>> desactivarPerfil(Authentication authentication) {
        perfilService.desactivarPerfil(authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Se eliminó correctamente su cuenta"));
    }
}
