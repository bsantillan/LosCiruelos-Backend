package com.LosCiruelos.padel_club_api.notificaciones.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Qué avisos de reservas quiere recibir el ADMIN. */
@Data
public class AvisosAdminRequest {
    // Interruptor general (opcional): false pausa todos los avisos sin perder la elección de cada uno
    private Boolean activos;
    @NotNull(message = "Es obligatorio")
    private Boolean reservaConfirmada;
    @NotNull(message = "Es obligatorio")
    private Boolean saldoCobrado;
    @NotNull(message = "Es obligatorio")
    private Boolean reservaCancelada;
    @NotNull(message = "Es obligatorio")
    private Boolean reembolsoManual;
}
