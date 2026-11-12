package com.LosCiruelos.padel_club_api.pagos.dto.request;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.ConceptoImputacion;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Cuánto se devuelve de una línea de un cobro: la cancha o un producto de la reserva. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LineaDevolucionRequest {

    @NotNull(message = "Indicá el concepto de cada línea (CANCHA o PRODUCTO)")
    private ConceptoImputacion concepto;

    // id de la línea de producto de la reserva (obligatorio si el concepto es PRODUCTO)
    private Long lineaId;

    @NotNull(message = "El monto de cada línea es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto de cada línea tiene que ser mayor a cero")
    @Digits(integer = 8, fraction = 2, message = "El monto puede tener hasta 2 decimales")
    private BigDecimal monto;
}
