package com.LosCiruelos.padel_club_api.club.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Hasta cuántos días adelante puede reservar el cliente. */
@Data
public class ReservasConfigRequest {
    @NotNull(message = "Es obligatorio")
    @Min(value = 1, message = "Mínimo 1 día")
    @Max(value = 365, message = "Máximo 365 días")
    private Integer diasAnticipacion;
}
