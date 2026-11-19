package com.LosCiruelos.padel_club_api.club.dto.request;

import com.LosCiruelos.padel_club_api.club.entity.enums.TipoCancha;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Alta o edición de una cancha (ADMIN). */
@Data
public class CanchaRequest {

    @NotNull(message = "El número de la cancha es obligatorio")
    @Min(value = 1, message = "El número de la cancha tiene que ser mayor a cero")
    private Integer numero;

    @Size(max = 40, message = "El nombre puede tener hasta 40 caracteres")
    private String nombre;

    @Size(max = 300, message = "La descripción puede tener hasta 300 caracteres")
    private String descripcion;

    @NotNull(message = "Indicá el tipo de cancha (TECHADA o AIRE_LIBRE)")
    private TipoCancha tipo;

    @Size(max = 60, message = "La superficie puede tener hasta 60 caracteres")
    private String superficie;

    @NotNull(message = "Indicá si la cancha tiene iluminación")
    private Boolean iluminacion;
}
