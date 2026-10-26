package com.LosCiruelos.padel_club_api.pagos.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PagoManualRequest {
    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    @Digits(integer = 8, fraction = 2, message = "El monto puede tener hasta 2 decimales")
    private BigDecimal monto;

    // Partido: qué jugador paga (obligatorio en un partido, no se manda en una reserva normal)
    private Long jugadorId;
}
