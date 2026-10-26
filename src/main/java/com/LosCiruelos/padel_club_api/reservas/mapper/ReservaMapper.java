package com.LosCiruelos.padel_club_api.reservas.mapper;

import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Services.ConfiguracionService;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.service.PoliticaCancelacion;

import lombok.RequiredArgsConstructor;

/**
 * Arma la respuesta de una reserva: los datos de la entidad más lo que depende de la
 * configuración y de la hora (seña, si el cliente la puede cancelar y cuánto se le devolvería, si
 * el turno ya se jugó y debe saldo).
 */
@Component
@RequiredArgsConstructor
public class ReservaMapper {

    private final ConfiguracionService configuracionService;
    private final PoliticaCancelacion politicaCancelacion;
    private final Reloj reloj;

    public ReservaResponse aResponse(Reserva reserva) {
        ReservaResponse response = ReservaResponse.from(reserva);
        response.setMontoSenia(configuracionService.calcularSenia(configuracionService.obtener(),
                reserva.getMontoTotal()));
        LocalDateTime ahora = reloj.ahora();

        // si el cliente la cancelara ahora: si puede, hasta cuándo y cuánto se le devolvería
        PoliticaCancelacion.Evaluacion cancelacion = politicaCancelacion.evaluarParaCliente(reserva, ahora);
        response.setCancelable(cancelacion.cancelable());
        response.setCancelableHasta(cancelacion.cancelableHasta());
        response.setMontoReembolso(cancelacion.montoReembolso());
        response.setReembolsoTotalHasta(cancelacion.reembolsoTotalHasta());
        response.setReembolsoSeniaHasta(cancelacion.reembolsoSeniaHasta());

        // turno jugado pero sin pagar del todo: el front lo muestra como "Jugada · saldo pendiente"
        boolean turnoTerminado = reserva.turnoTermino(ahora);
        response.setTurnoTerminado(turnoTerminado);
        response.setDebeSaldo(turnoTerminado && Reserva.ESTADOS_CON_SALDO.contains(reserva.getEstado()));
        return response;
    }

    /** La respuesta para quien la pide: a un cliente no se le muestran los teléfonos de los otros jugadores. */
    public ReservaResponse aResponsePara(Reserva reserva, Usuario usuario) {
        ReservaResponse response = aResponse(reserva);
        if (!Roles.esPersonal(usuario)) {
            response.getJugadores().forEach(j -> j.setTelefono(null));
        }
        return response;
    }
}
