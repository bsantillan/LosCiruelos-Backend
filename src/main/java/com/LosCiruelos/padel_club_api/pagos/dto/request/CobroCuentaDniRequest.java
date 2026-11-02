package com.LosCiruelos.padel_club_api.pagos.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Pago con Cuenta DNI que registra el personal: el monto y el número de comprobante de la app. */
@Data
public class CobroCuentaDniRequest {
    @NotNull(message = "El monto es obligatorio")
    @DecimalMin(value = "0.01", message = "El monto debe ser mayor a cero")
    @Digits(integer = 8, fraction = 2, message = "El monto puede tener hasta 2 decimales")
    private BigDecimal monto;

    @NotBlank(message = "Indicá el número de comprobante de Cuenta DNI")
    @Size(max = 64, message = "El comprobante puede tener hasta 64 caracteres")
    @Pattern(regexp = "\\s*[A-Za-z0-9-]+\\s*", message = "El comprobante solo puede tener letras, números y guiones")
    private String comprobante;
}
