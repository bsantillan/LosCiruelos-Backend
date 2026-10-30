package com.LosCiruelos.padel_club_api.reservas.dto.request;

import com.LosCiruelos.padel_club_api.pagos.dto.request.BonificacionRequest;

import jakarta.validation.Valid;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Reserva que carga el personal para un cliente (POST /admin/reservas). */
@Data
@EqualsAndHashCode(callSuper = true)
public class CrearReservaAdminRequest extends CrearReservaRequest {

    // obligatorio: el cliente de la reserva (se valida en el service, con el mismo error de siempre)
    Long clienteId;

    // Solo ADMIN (opcional): bonificar el turno al crearlo, todo o una parte
    @Valid
    BonificacionRequest bonificacion;
}
