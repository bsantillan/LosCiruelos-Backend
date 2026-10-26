package com.LosCiruelos.padel_club_api.reservas.entity.enums;

public enum EstadoReserva {
    PENDIENTE,
    // Partido armado por el ADMIN sin nada pagado: el turno está confirmado y se cobra en el club
    CONFIRMADA,
    SEÑADA,
    PARCIALMENTE_PAGADA,
    PAGADA,
    CANCELADA,
    EXPIRADA,
    COMPLETADA
}
