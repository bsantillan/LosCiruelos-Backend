package com.LosCiruelos.padel_club_api.usuarios.controller;

import com.LosCiruelos.padel_club_api.usuarios.dto.response.ClienteBusquedaResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.usuarios.dto.request.CrearUsuarioAdminRequest;
import com.LosCiruelos.padel_club_api.usuarios.dto.request.EditarUsuarioAdminRequest;
import com.LosCiruelos.padel_club_api.usuarios.dto.request.FiltroUsuarioRequest;
import com.LosCiruelos.padel_club_api.usuarios.dto.response.UsuarioAdminResponse;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioAdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Usuarios en el panel del club: ABM solo del ADMIN; la búsqueda de clientes también la usa el
 * EMPLEADO.
 */
@RestController
@RequestMapping("/admin/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioAdminController {

    private final UsuarioAdminService usuarioAdminService;

    @PostMapping
    public ResponseEntity<UsuarioAdminResponse> crearUsuario(
            @Valid @RequestBody CrearUsuarioAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioAdminService.crearUsuario(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioAdminResponse> editarUsuario(
            @PathVariable Long id,
            @Valid @RequestBody EditarUsuarioAdminRequest request) {
        return ResponseEntity.ok(usuarioAdminService.editarUsuario(id, request));
    }

    @GetMapping
    public ResponseEntity<List<UsuarioAdminResponse>> listarUsuarios(
            @ModelAttribute FiltroUsuarioRequest filtros) {
        return ResponseEntity.ok(usuarioAdminService.listarUsuarios(filtros));
    }

    /** Clientes por nombre, apellido o email (mínimo 2 caracteres, hasta 20 resultados). */
    @GetMapping("/clientes")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
    public ResponseEntity<List<ClienteBusquedaResponse>> buscarClientes(
            @RequestParam String buscar,
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        return ResponseEntity.ok(usuarioAdminService.buscarClientes(buscar, soloActivos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioAdminResponse> getUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioAdminService.getUsuario(id));
    }

    @PutMapping("/{id}/reactivar")
    public ResponseEntity<UsuarioAdminResponse> reactivarUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioAdminService.reactivarUsuario(id));
    }

    @DeleteMapping("/{id}/desactivar")
    public ResponseEntity<UsuarioAdminResponse> desactivarUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(usuarioAdminService.desactivarUsuario(id));
    }
}