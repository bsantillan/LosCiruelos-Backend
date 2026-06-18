package com.LosCiruelos.padel_club_api.Controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.DTOs.Requests.CrearReservaRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ReservaResponse;
import com.LosCiruelos.padel_club_api.Security.UsuarioPrincipal;
import com.LosCiruelos.padel_club_api.Services.ReservaExpirationService;
import com.LosCiruelos.padel_club_api.Services.ReservaService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/reservas")
@RequiredArgsConstructor
public class ReservaController {

    private final ReservaService reservaService;
    private final ReservaExpirationService reservaExpirationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'EMPLEADO')")
    public ResponseEntity<ReservaResponse> crearReserva(
            @Valid @RequestBody CrearReservaRequest request,
            Authentication authentication) {
        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        ReservaResponse response = reservaService.crearReserva(request, principal.getUsername());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/expirar")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'EMPLEADO')")
    public ResponseEntity<Void> expirarReserva(@PathVariable Long id) {
        reservaExpirationService.expirarReserva(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN', 'EMPLEADO')")
    public ResponseEntity<Void> cancelarReserva(
            @PathVariable Long id,
            Authentication authentication) {
        UsuarioPrincipal principal = (UsuarioPrincipal) authentication.getPrincipal();
        reservaService.cancelarReserva(id, principal.getUsername());
        return ResponseEntity.ok().build();
    }

}
