package com.LosCiruelos.padel_club_api.ventas.dto.request;

import lombok.Data;

/** El cliente al que se asocia una venta; null la deja como venta al público. */
@Data
public class ClienteVentaRequest {
    private Long clienteId;
}
