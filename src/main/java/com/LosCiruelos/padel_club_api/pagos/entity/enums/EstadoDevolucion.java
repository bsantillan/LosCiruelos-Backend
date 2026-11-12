package com.LosCiruelos.padel_club_api.pagos.entity.enums;

/** En qué está la devolución de (parte de) un cobro. */
public enum EstadoDevolucion {
    A_REEMBOLSAR,        // hay que devolverla a mano (cobro en el club o reembolso de MP que no se pudo hacer)
    REEMBOLSO_PENDIENTE, // pedida a Mercado Pago, que todavía no la aceptó: un job la reintenta
    DEVUELTO             // entregada al cliente (o confirmada por Mercado Pago)
}
