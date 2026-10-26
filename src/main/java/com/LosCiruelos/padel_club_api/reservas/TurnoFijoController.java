package com.LosCiruelos.padel_club_api.reservas;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
import org.springframework.web.bind.annotation.RestController;

import com.LosCiruelos.padel_club_api.reservas.dto.TurnoFijoRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.ReservaResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.TurnoFijoResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Turnos fijos del ADMIN. Al crear o modificar uno, primero se guarda y después se crean sus
 * partidos de las próximas semanas, cada uno en su transacción (los que no se pueden crear se
 * informan en fechasOmitidas).
 */
@RestController
@RequestMapping("/admin/turnos-fijos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class TurnoFijoController {

    private final TurnoFijoService turnoFijoService;
    private final GeneradorTurnosFijos generador;

    /** @param activos true: solo activos · false: solo dados de baja · sin indicar: todos */
    @GetMapping
    public ResponseEntity<List<TurnoFijoResponse>> listar(@RequestParam(required = false) Boolean activos) {
        return ResponseEntity.ok(turnoFijoService.listar(activos));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TurnoFijoResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(turnoFijoService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<TurnoFijoResponse> crear(@Valid @RequestBody TurnoFijoRequest request,
            Authentication authentication) {
        Long id = turnoFijoService.crear(request, authentication.getName());
        GeneradorTurnosFijos.Resultado resultado = generador.generar(id);
        TurnoFijoResponse response = turnoFijoService.obtener(id);
        response.setFechasOmitidas(resultado.omitidas());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Se manda completo. Los partidos futuros sin pagos se vuelven a crear con los datos nuevos; los
     * que tienen pagos quedan como estaban (partidosSinCambiar).
     */
    @PutMapping("/{id}")
    public ResponseEntity<TurnoFijoResponse> actualizar(@PathVariable Long id,
            @Valid @RequestBody TurnoFijoRequest request, Authentication authentication) {
        List<Long> sinCambiar = turnoFijoService.actualizar(id, request, authentication.getName());
        GeneradorTurnosFijos.Resultado resultado = generador.generar(id);
        TurnoFijoResponse response = turnoFijoService.obtener(id);
        response.setFechasOmitidas(resultado.omitidas());
        response.setPartidosSinCambiar(sinCambiar);
        return ResponseEntity.ok(response);
    }

    /** Baja general: se cancelan los partidos que todavía no empezaron (reembolso si tienen plata cobrada). */
    @DeleteMapping("/{id}")
    public ResponseEntity<TurnoFijoResponse> darDeBaja(@PathVariable Long id,
            @RequestParam(required = false) OpcionReembolso reembolso, Authentication authentication) {
        return ResponseEntity.ok(turnoFijoService.darDeBaja(id, reembolso, authentication.getName()));
    }

    /** Baja de una sola fecha (esa semana no vienen): el horario de ese día queda libre. */
    @DeleteMapping("/{id}/fechas/{fecha}")
    public ResponseEntity<ReservaResponse> darDeBajaFecha(@PathVariable Long id,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) OpcionReembolso reembolso, Authentication authentication) {
        return ResponseEntity.ok(turnoFijoService.darDeBajaFecha(id, fecha, reembolso, authentication.getName()));
    }
}
