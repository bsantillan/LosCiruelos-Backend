package com.LosCiruelos.padel_club_api.reservas.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Producto que el personal suma a una reserva o un partido: uno del catálogo (productoId) o uno
 * que no está cargado (nombre y precio), que queda dado de alta. Va uno u otro.
 */
@Data
public class AgregarProductoRequest {
    private Long productoId;

    @Size(min = 2, max = 100, message = "El nombre tiene que tener entre 2 y 100 caracteres")
    private String nombre;

    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio puede tener hasta 2 decimales")
    private BigDecimal precio;

    @NotNull(message = "Indicá la cantidad")
    @Min(value = 1, message = "La cantidad tiene que ser al menos 1")
    @Max(value = 99, message = "Se pueden agregar hasta 99 unidades")
    private Integer cantidad;
}
