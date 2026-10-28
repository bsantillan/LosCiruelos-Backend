package com.LosCiruelos.padel_club_api.usuarios.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.usuarios.dto.response.ClienteBusquedaResponse;
import com.LosCiruelos.padel_club_api.usuarios.service.BusquedaClientesService;

import lombok.RequiredArgsConstructor;

/**
 * Búsqueda de clientes del panel del personal (ADMIN y EMPLEADO).
 * Pendiente para la rama de feature: pasa a /admin/usuarios?rol=CLIENTE.
 */
@RestController
@RequestMapping("/admin/clientes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLEADO')")
public class ClienteAdminController {

    private final BusquedaClientesService busquedaClientesService;

    /** Clientes por nombre, apellido o email (mínimo 2 caracteres, hasta 20 resultados). */
    @GetMapping
    public ResponseEntity<List<ClienteBusquedaResponse>> buscar(
            @RequestParam String buscar,
            @RequestParam(defaultValue = "true") boolean soloActivos) {
        return ResponseEntity.ok(busquedaClientesService.buscar(buscar, soloActivos));
    }
}
