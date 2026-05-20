package com.LosCiruelos.padel_club_api.Entities.Enum;

public enum EstadoPago {
    PENDIENTE,      // preferencia generada, esperando pago
    APROBADO,       // pago confirmado
    RECHAZADO,      // pago rechazado o cancelado
    EN_PROCESO,     // en revisión por MP o esperando acreditación
    DEVUELTO        // reembolsado o contracargo
}