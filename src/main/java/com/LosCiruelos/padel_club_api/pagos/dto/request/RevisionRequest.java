package com.LosCiruelos.padel_club_api.pagos.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** El ADMIN marca una devolución del EMPLEADO como revisada (true) o la desmarca (false). */
@Data
public class RevisionRequest {
    @NotNull(message = "Indicá si la devolución está revisada (true o false)")
    private Boolean revisada;
}
