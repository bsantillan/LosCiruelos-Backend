package com.LosCiruelos.padel_club_api.DTOs.Requests;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Bonificación del ADMIN: parte (o todo) del turno que no se le cobra al cliente. */
@Data
public class BonificacionRequest {

    // Opcional: sin monto se bonifica todo lo que falta pagar
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    @Digits(integer = 8, fraction = 2, message = "El monto puede tener hasta 2 decimales")
    private BigDecimal monto;

    // Opcional, ej. "socio", "torneo", "cortesía"
    @Size(max = 200, message = "El motivo puede tener hasta 200 caracteres")
    private String motivo;
}
