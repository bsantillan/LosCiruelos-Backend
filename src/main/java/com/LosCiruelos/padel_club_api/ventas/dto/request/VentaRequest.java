package com.LosCiruelos.padel_club_api.ventas.dto.request;

import java.math.BigDecimal;
import java.util.List;

import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Venta suelta que registra el personal: qué se vende, cómo se cobró y, si se quiere, a qué cliente. */
@Data
public class VentaRequest {
    @NotEmpty(message = "Indicá qué se vende")
    @Size(max = 20, message = "Se pueden vender hasta 20 productos distintos")
    private List<@Valid @NotNull(message = "Indicá qué se vende") Item> items;

    @NotNull(message = "Indicá cómo se cobró: EFECTIVO, MERCADO_PAGO, TRANSFERENCIA_MP o CUENTA_DNI")
    private MedioDePago medio;

    // opcional
    private Long clienteId;

    /**
     * Un producto del catálogo (productoId) o uno que no está cargado (nombre y precio), que queda
     * dado de alta. Va uno u otro.
     */
    @Data
    public static class Item {
        private Long productoId;

        @Size(min = 2, max = 100, message = "El nombre tiene que tener entre 2 y 100 caracteres")
        private String nombre;

        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
        @Digits(integer = 8, fraction = 2, message = "El precio puede tener hasta 2 decimales")
        private BigDecimal precio;

        @NotNull(message = "Indicá la cantidad")
        @Min(value = 1, message = "La cantidad tiene que ser al menos 1")
        @Max(value = 99, message = "Se pueden vender hasta 99 unidades")
        private Integer cantidad;
    }
}
