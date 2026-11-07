package com.LosCiruelos.padel_club_api.ventas.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Lo que el cliente devuelve de una venta: qué productos, cuántas unidades y si vuelven al stock. */
@Data
public class DevolucionVentaRequest {
    @NotBlank(message = "Indicá por qué se devuelve")
    @Size(max = 200, message = "El motivo puede tener hasta 200 caracteres")
    private String motivo;

    @NotEmpty(message = "Indicá qué se devuelve")
    @Size(max = 20, message = "Se pueden devolver hasta 20 productos distintos")
    private List<@Valid @NotNull(message = "Indicá qué se devuelve") Item> items;

    @Data
    public static class Item {
        @NotNull(message = "Indicá el producto")
        private Long productoId;

        @NotNull(message = "Indicá la cantidad")
        @Min(value = 1, message = "La cantidad mínima es 1")
        @Max(value = 99, message = "La cantidad máxima es 99")
        private Integer cantidad;

        // false si el producto no sirve (abierto, roto): no vuelve al stock
        @NotNull(message = "Indicá si vuelve al stock (true o false)")
        private Boolean vuelveAlStock;
    }
}
