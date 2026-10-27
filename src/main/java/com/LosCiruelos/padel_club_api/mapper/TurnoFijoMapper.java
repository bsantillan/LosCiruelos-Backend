package com.LosCiruelos.padel_club_api.mapper;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.reservas.dto.response.TurnoFijoResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijo;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijoJugador;

/** TurnoFijo → TurnoFijoResponse, con los próximos partidos que ya buscó el service. */
@Component
public class TurnoFijoMapper {

    public TurnoFijoResponse aResponse(TurnoFijo tf, List<Reserva> proximosPartidos) {
        TurnoFijoResponse response = new TurnoFijoResponse();
        response.setId(tf.getId());
        response.setCanchaId(tf.getCancha().getId());
        response.setCanchaNumero(tf.getCancha().getNumero());
        response.setDiaSemana(tf.getDiaSemana());
        response.setHoraInicio(tf.getHoraInicio());
        response.setHoraFin(tf.getHoraFin());
        response.setDesde(tf.getDesde());
        response.setCantidadPelotas(tf.getCantidadPelotas());
        response.setCantidadPaletas(tf.getCantidadPaletas());
        response.setActivo(tf.getActivo());
        response.setCreatedAt(tf.getCreatedAt());
        response.setDadoDeBajaAt(tf.getDadoDeBajaAt());
        response.setJugadores(tf.getJugadores().stream().map(TurnoFijoMapper::jugador).toList());
        response.setProximosPartidos(proximosPartidos.stream()
                .map(r -> new TurnoFijoResponse.Partido(r.getId(), r.getFechaReserva(), r.getEstado(),
                        r.getMontoTotal(), r.estaInactiva() ? BigDecimal.ZERO : r.getMontoPendiente()))
                .toList());
        return response;
    }

    private static TurnoFijoResponse.Jugador jugador(TurnoFijoJugador j) {
        return new TurnoFijoResponse.Jugador(j.getPosicion(),
                j.getUsuario() != null ? j.getUsuario().getId() : null, j.nombreParaMostrar(),
                j.getUsuario() != null ? j.getUsuario().getTelefono() : j.getTelefono(), j.getUsuario() == null);
    }
}
