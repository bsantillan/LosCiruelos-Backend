package com.LosCiruelos.padel_club_api.club.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PublicacionRequest {
    @NotNull(message = "Indicá si la novedad está publicada (true o false)")
    private Boolean publicada;
}
