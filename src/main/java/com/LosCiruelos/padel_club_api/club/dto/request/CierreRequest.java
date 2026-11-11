package com.LosCiruelos.padel_club_api.club.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** El ADMIN cierra el club un día puntual (feriado, mantenimiento). */
@Data
public class CierreRequest {
    @NotNull(message = "Indicá la fecha (yyyy-MM-dd)")
    private LocalDate fecha;

    @NotBlank(message = "Indicá el motivo (lo ven los clientes)")
    @Size(max = 200, message = "El motivo puede tener hasta 200 caracteres")
    private String motivo;

    // opcional: true cierra aunque haya reservas ese día; se cancelan con reembolso total
    private Boolean confirmar;
}
