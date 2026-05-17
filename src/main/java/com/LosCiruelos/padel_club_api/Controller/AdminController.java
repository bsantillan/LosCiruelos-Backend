package com.LosCiruelos.padel_club_api.Controller;

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

import com.LosCiruelos.padel_club_api.DTOs.Requests.CrearUsuarioAdminRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.EditarUsuarioAdminRequest;
import com.LosCiruelos.padel_club_api.DTOs.Requests.FiltroUsuarioRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.UsuarioAdminResponse;
import com.LosCiruelos.padel_club_api.Services.AdminService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/admin/usuarios")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping
    public ResponseEntity<UsuarioAdminResponse> crearUsuario(
            @Valid @RequestBody CrearUsuarioAdminRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.crearUsuario(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioAdminResponse> editarUsuario(
            @PathVariable Long id,
            @Valid @RequestBody EditarUsuarioAdminRequest request) {
        return ResponseEntity.ok(adminService.editarUsuario(id, request));
    }

    @GetMapping
    public ResponseEntity<List<UsuarioAdminResponse>> listarUsuarios(
            @ModelAttribute FiltroUsuarioRequest filtros) {
        return ResponseEntity.ok(adminService.listarUsuarios(filtros));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioAdminResponse> getUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUsuario(id));
    }

    @PutMapping("/{id}/reactivar")
    public ResponseEntity<UsuarioAdminResponse> reactivarUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.reactivarUsuario(id));
    }

    @DeleteMapping("/{id}/desactivar")
    public ResponseEntity<UsuarioAdminResponse> desactivarUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.desactivarUsuario(id));
    }
}