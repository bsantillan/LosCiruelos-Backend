package com.LosCiruelos.padel_club_api.club.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Alta o edición de una novedad (ADMIN). */
@Data
public class NovedadRequest {

    @NotBlank(message = "El título es obligatorio")
    @Size(max = 80, message = "El título puede tener hasta 80 caracteres")
    private String titulo;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(max = 300, message = "La descripción puede tener hasta 300 caracteres")
    private String descripcion;

    @Size(max = 30, message = "La etiqueta puede tener hasta 30 caracteres")
    private String etiqueta;

    // opcional: después de esta fecha deja de mostrarse sola
    private LocalDate visibleHasta;
}
