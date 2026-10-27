package com.LosCiruelos.padel_club_api.club.dto.request;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ProductoRequest {
    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100, message = "El nombre puede tener hasta 100 caracteres")
    private String nombre;

    @Size(max = 300, message = "La descripción puede tener hasta 300 caracteres")
    private String descripcion;

    // VENTA o ALQUILER; no se puede cambiar una vez creado
    @NotNull(message = "El tipo es obligatorio")
    private TipoProducto tipo;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.00", message = "El precio no puede ser negativo")
    @Digits(integer = 8, fraction = 2, message = "El precio puede tener hasta 2 decimales")
    private BigDecimal precio;

    // VENTA: unidades disponibles · ALQUILER: unidades totales
    @NotNull(message = "El stock es obligatorio")
    @Min(value = 0, message = "El stock no puede ser negativo")
    private Integer stock;

    // por defecto true
    private Boolean activo;
}
