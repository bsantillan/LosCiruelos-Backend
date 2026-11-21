package com.LosCiruelos.padel_club_api.club.dto.response;

/**
 * Si los clientes pueden reservar desde la web (público: el front lo consulta antes de mostrar el asistente).
 *
 * @param habilitadas false: el ADMIN pausó las reservas online (el personal sigue cargando reservas)
 * @param mensaje     pausadas: lo que escribió el ADMIN para los clientes (null si no escribió nada o si
 *                    están habilitadas)
 */
public record ReservasOnlineResponse(boolean habilitadas, String mensaje) {
}
