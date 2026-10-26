package com.LosCiruelos.padel_club_api.pagos;

public enum EstadoPago {
    PENDIENTE,      // preferencia generada, esperando pago
    APROBADO,       // pago confirmado
    RECHAZADO,      // pago rechazado o cancelado
    EN_PROCESO,     // en revisión por MP o esperando acreditación
    DEVUELTO,       // reembolsado o contracargo
    EXPIRADO,       // nunca se cobró y la reserva expiró o se canceló
    A_REEMBOLSAR,   // hay que devolverlo a mano (pago en el club, cobro sobre una reserva inactiva o reembolso de MP que no se pudo hacer)
    REEMBOLSO_PENDIENTE // reembolso online pedido que MP todavía no aceptó (ej. el pago se está acreditando): un job lo reintenta
}