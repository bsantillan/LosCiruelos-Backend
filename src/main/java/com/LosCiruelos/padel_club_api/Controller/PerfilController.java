package com.LosCiruelos.padel_club_api.Controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.DTOs.Requests.PerfilRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PerfilResponse;
import com.LosCiruelos.padel_club_api.Security.UsuarioPrincipal;
import com.LosCiruelos.padel_club_api.Services.PerfilService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/perfil")
@RequiredArgsConstructor
public class PerfilController {

    private final PerfilService perfilService;

    @GetMapping
    public ResponseEntity<PerfilResponse> getPerfil(Authentication authentication) {
        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        return ResponseEntity.ok(perfilService.getPerfil(principal.getUsername()));
    }

    @PutMapping
    public ResponseEntity<PerfilResponse> updatePerfil(
            @RequestBody PerfilRequest per_rq,
            Authentication authentication) {
        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();

        return ResponseEntity.ok(perfilService.updatePerfil(per_rq, principal.getUsername()));
    }

    @DeleteMapping("/desactivar")
    public ResponseEntity<?> desactivarPerfil(Authentication authentication, @RequestBody Map<String, String> request) {
        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        String refreshToken = request.get("refreshToken");
        perfilService.desactivarPerfil(principal.getUsername(), refreshToken);
        return ResponseEntity.ok(Map.of("message", "Se elimino correctamente su cuenta"));
    }
}
