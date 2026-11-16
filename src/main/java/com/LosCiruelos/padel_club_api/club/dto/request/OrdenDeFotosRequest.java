package com.LosCiruelos.padel_club_api.club.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Las fotos de la cancha en el orden nuevo (todas, por id). */
@Data
public class OrdenDeFotosRequest {
    @NotNull(message = "Mandá las fotos en el orden nuevo")
    private List<Long> fotos;
}
