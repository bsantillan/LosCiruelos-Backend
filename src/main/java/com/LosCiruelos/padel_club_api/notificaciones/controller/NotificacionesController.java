package com.LosCiruelos.padel_club_api.notificaciones.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.notificaciones.dto.request.AvisosAdminRequest;
import com.LosCiruelos.padel_club_api.notificaciones.dto.request.CodigoTelefonoRequest;
import com.LosCiruelos.padel_club_api.notificaciones.dto.request.NotificacionesRequest;
import com.LosCiruelos.padel_club_api.notificaciones.dto.request.SuscripcionesRequest;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.AvisosAdminResponse;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.NotificacionesResponse;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.PruebaAvisoResponse;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.SuscripcionesResponse;
import com.LosCiruelos.padel_club_api.notificaciones.service.NotificacionesService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Notificaciones del usuario autenticado: canal (mail o WhatsApp) y teléfono, los avisos que elige
 * el CLIENTE y los avisos de reservas del ADMIN.
 */
@RestController
@RequiredArgsConstructor
public class NotificacionesController {

    private final NotificacionesService notificacionesService;

    // ── Perfil (cualquier rol) ───────────────────────────────────────────

    @GetMapping("/perfil/notificaciones")
    public ResponseEntity<NotificacionesResponse> obtener(Authentication authentication) {
        return ResponseEntity.ok(notificacionesService.obtener(authentication.getName()));
    }

    @PutMapping("/perfil/notificaciones")
    public ResponseEntity<NotificacionesResponse> actualizar(@Valid @RequestBody NotificacionesRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(notificacionesService.actualizar(authentication.getName(), request.getCanal(),
                request.getTelefono()));
    }

    @PostMapping("/perfil/telefono/codigo")
    public ResponseEntity<Void> enviarCodigo(Authentication authentication) {
        notificacionesService.enviarCodigo(authentication.getName());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/perfil/telefono/verificar")
    public ResponseEntity<NotificacionesResponse> verificar(@Valid @RequestBody CodigoTelefonoRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(notificacionesService.verificarTelefono(authentication.getName(), request.getCodigo()));
    }

    // ── Avisos que elige el CLIENTE ──────────────────────────────────────

    @GetMapping("/perfil/suscripciones")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<SuscripcionesResponse> suscripciones(Authentication authentication) {
        return ResponseEntity.ok(notificacionesService.suscripciones(authentication.getName()));
    }

    @PutMapping("/perfil/suscripciones")
    @PreAuthorize("hasRole('CLIENTE')")
    public ResponseEntity<SuscripcionesResponse> actualizarSuscripciones(
            @Valid @RequestBody SuscripcionesRequest request, Authentication authentication) {
        return ResponseEntity.ok(notificacionesService.actualizarSuscripciones(authentication.getName(), request));
    }

    // ── Avisos de reservas del ADMIN ─────────────────────────────────────

    @GetMapping("/perfil/avisos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AvisosAdminResponse> avisos(Authentication authentication) {
        return ResponseEntity.ok(notificacionesService.avisosAdmin(authentication.getName()));
    }

    @PutMapping("/perfil/avisos")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AvisosAdminResponse> actualizarAvisos(@Valid @RequestBody AvisosAdminRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(notificacionesService.actualizarAvisosAdmin(authentication.getName(), request));
    }

    @PostMapping("/perfil/avisos/prueba")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PruebaAvisoResponse> probar(Authentication authentication) {
        return ResponseEntity.ok(notificacionesService.probarAviso(authentication.getName()));
    }
}
