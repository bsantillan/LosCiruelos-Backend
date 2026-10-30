package com.LosCiruelos.padel_club_api.club.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Recordatorio del turno al cliente: si se manda y con cuántos minutos de anticipación. */
@Data
public class RecordatorioConfigRequest {
    @NotNull(message = "Es obligatorio")
    private Boolean recordatorioActivo;

    @NotNull(message = "Es obligatorio")
    @Min(value = 15, message = "Mínimo 15 minutos")
    @Max(value = 1440, message = "Máximo 1440 minutos (24 h)")
    private Integer minutosRecordatorio;
}
