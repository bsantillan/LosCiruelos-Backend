package com.LosCiruelos.padel_club_api.club.dto.response;

import java.util.List;

/**
 * Resultado de dar de baja una cancha con {@code confirmar=true}: la cancha, las reservas que se
 * cancelaron (y las que no se pudieron, con el motivo) y los turnos fijos que se dieron de baja.
 */
public record BajaDeCanchaResponse(CanchaResponse cancha, CancelacionesResponse cancelaciones,
        List<Long> turnosFijosDadosDeBaja) {
}
