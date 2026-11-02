package com.LosCiruelos.padel_club_api.pagos.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.Data;

/** QR para cobrar en el club. Sin monto, se cobra todo lo que falta pagar. */
@Data
public class CobroQrRequest {
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    @Digits(integer = 8, fraction = 2, message = "El monto puede tener hasta 2 decimales")
    private BigDecimal monto;
}
