package com.LosCiruelos.padel_club_api.mapper;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.club.dto.response.FueraDeHorarioResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijo;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;

/** Reservas y turnos fijos fuera del horario nuevo → la lista del 409 de PUT /admin/configuracion. */
@Component
public class FueraDeHorarioMapper {

    public FueraDeHorarioResponse.Reserva aReserva(Reserva r) {
        Usuario cliente = r.getCliente();
        return new FueraDeHorarioResponse.Reserva(r.getId(), r.getFechaReserva(), r.getHoraInicio(), r.getHoraFin(),
                r.getCancha().getNumero(),
                cliente != null ? (cliente.getNombre() + " " + cliente.getApellido()).trim() : null,
                r.getTipo().name());
    }

    public FueraDeHorarioResponse.TurnoFijo aTurnoFijo(TurnoFijo tf) {
        return new FueraDeHorarioResponse.TurnoFijo(tf.getId(), tf.getDiaSemana(), tf.getHoraInicio(), tf.getHoraFin(),
                tf.getCancha().getNumero());
    }
}
