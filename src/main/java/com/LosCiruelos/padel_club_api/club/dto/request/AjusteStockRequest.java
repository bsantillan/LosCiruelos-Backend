package com.LosCiruelos.padel_club_api.club.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AjusteStockRequest {
    // positivo para sumar (reposición), negativo para restar (rotura, pérdida)
    @NotNull(message = "El ajuste es obligatorio")
    private Integer ajuste;
}
