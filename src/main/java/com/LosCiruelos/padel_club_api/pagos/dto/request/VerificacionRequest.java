package com.LosCiruelos.padel_club_api.pagos.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** El ADMIN marca un pago con Cuenta DNI como verificado (true) o lo desmarca (false). */
@Data
public class VerificacionRequest {
    @NotNull(message = "Indicá si el pago está verificado (true o false)")
    private Boolean verificado;
}
