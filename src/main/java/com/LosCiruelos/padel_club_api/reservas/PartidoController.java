package com.LosCiruelos.padel_club_api.reservas;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.reservas.dto.PartidoRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.ReservaResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Partidos de 4 jugadores que arma el ADMIN. Se consultan, se cobran y se cancelan con los
 * endpoints de siempre: GET /reservas/{id}, POST /pagos/{id}/pago-efectivo (con jugadorId) y
 * DELETE /reservas/{id}/cancelar.
 */
@RestController
@RequestMapping("/admin/partidos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PartidoController {

    private final PartidoService partidoService;

    @PostMapping
    public ResponseEntity<ReservaResponse> crear(@Valid @RequestBody PartidoRequest request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(partidoService.crear(request, authentication.getName()));
    }

    /** Se manda el partido completo: cancha, fecha, horario, los 4 jugadores y productos. */
    @PutMapping("/{id}")
    public ResponseEntity<ReservaResponse> actualizar(@PathVariable Long id, @Valid @RequestBody PartidoRequest request,
            Authentication authentication) {
        return ResponseEntity.ok(partidoService.actualizar(id, request, authentication.getName()));
    }
}
