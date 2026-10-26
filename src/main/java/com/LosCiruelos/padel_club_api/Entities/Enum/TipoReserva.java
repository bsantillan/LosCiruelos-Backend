package com.LosCiruelos.padel_club_api.Entities.Enum;

public enum TipoReserva {
    // Un cliente reserva para él (o el personal en su nombre); vence si no se paga la seña
    NORMAL,
    // Partido de 4 jugadores armado por el ADMIN: no vence y cada jugador paga su parte en el club
    PARTIDO
}
