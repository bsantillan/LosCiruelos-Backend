package com.LosCiruelos.padel_club_api.reservas.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DevolucionRequest {
    // paletas que volvieron; las que falten se descuentan del stock (rotas o perdidas)
    @NotNull(message = "La cantidad de paletas devueltas es obligatoria")
    @Min(value = 0, message = "La cantidad devuelta no puede ser negativa")
    private Integer paletasDevueltas;
}
