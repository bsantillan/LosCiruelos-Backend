package com.LosCiruelos.padel_club_api.DTOs.Requests;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AjusteStockRequest {
    // positivo para sumar (reposición), negativo para restar (rotura, pérdida)
    @NotNull(message = "El ajuste es obligatorio")
    private Integer ajuste;
}
