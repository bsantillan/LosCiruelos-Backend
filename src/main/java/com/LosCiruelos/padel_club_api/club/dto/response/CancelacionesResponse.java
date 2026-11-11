package com.LosCiruelos.padel_club_api.club.dto.response;

import java.util.List;

/**
 * Las reservas que se cancelaron (con reembolso total y aviso al cliente) al confirmar un cambio
 * de horario o un día cerrado, y las que no se pudieron cancelar, con el motivo (por ejemplo, un
 * pago en proceso): esas quedan para que el ADMIN las cancele a mano.
 */
public record CancelacionesResponse(List<Long> canceladas, List<SinCancelar> sinCancelar) {

    public static final CancelacionesResponse NINGUNA = new CancelacionesResponse(List.of(), List.of());

    public record SinCancelar(Long reservaId, String motivo) {
    }
}
