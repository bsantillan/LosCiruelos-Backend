package com.LosCiruelos.padel_club_api.pagos.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Qué se le devuelve al cliente de un cobro, línea por línea (para devolverlo todo, todas las líneas). */
@Data
public class DevolucionRequest {

    @NotEmpty(message = "Indicá al menos una línea a devolver")
    @Valid
    private List<LineaDevolucionRequest> lineas;

    @Size(max = 200, message = "El motivo puede tener hasta 200 caracteres")
    private String motivo;

    // Cobros del club: true (por defecto) se le dio la plata al cliente en el momento; false, queda
    // A_REEMBOLSAR hasta que se entregue. En Mercado Pago se ignora: la devuelve la API.
    private Boolean entregada;

    public boolean seEntregaEnElMomento() {
        return !Boolean.FALSE.equals(entregada);
    }
}
