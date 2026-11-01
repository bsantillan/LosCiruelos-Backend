package com.LosCiruelos.padel_club_api.reservas.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Un producto del catálogo que se pide en una reserva, un partido o un turno fijo. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoPedidoRequest {
    @NotNull(message = "Indicá el producto (productoId)")
    private Long productoId;

    @NotNull(message = "Indicá la cantidad")
    @Min(value = 1, message = "La cantidad tiene que ser al menos 1")
    @Max(value = 99, message = "Se pueden pedir hasta 99 unidades")
    private Integer cantidad;
}
