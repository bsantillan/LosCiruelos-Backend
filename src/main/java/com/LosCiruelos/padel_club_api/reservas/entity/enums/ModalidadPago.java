package com.LosCiruelos.padel_club_api.reservas.entity.enums;

/** Cómo se paga una reserva que carga el personal (las del propio cliente no tienen: pagan online). */
public enum ModalidadPago {
    // el cliente está en el club: PENDIENTE que vence a los 15 minutos si no se cobra la seña
    EN_EL_CLUB,
    // el cliente no está: PENDIENTE con un plazo más largo y el link para pagar la seña online
    SENIA_ONLINE,
    // solo ADMIN: CONFIRMADA sin pagos, como un partido; se cobra en el club
    SIN_SENIA
}
