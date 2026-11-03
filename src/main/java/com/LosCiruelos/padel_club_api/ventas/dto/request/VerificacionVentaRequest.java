package com.LosCiruelos.padel_club_api.ventas.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** El ADMIN marca una venta con Cuenta DNI como verificada (true) o la desmarca (false). */
@Data
public class VerificacionVentaRequest {
    @NotNull(message = "Indicá si la venta está verificada (true o false)")
    private Boolean verificada;
}
