package com.LosCiruelos.padel_club_api.pagos.entity.enums;

/** Un movimiento del cierre de caja. */
public enum TipoMovimientoCaja {
    COBRO,       // cobro de una reserva o un partido (entra)
    VENTA,       // venta suelta (entra)
    DEVOLUCION,  // plata que se le devolvió al cliente (sale)
    ANULACION    // un cobro o una venta cargados por error (resta lo que se había sumado)
}
