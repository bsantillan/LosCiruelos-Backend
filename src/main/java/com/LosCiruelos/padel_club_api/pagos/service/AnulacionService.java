package com.LosCiruelos.padel_club_api.pagos.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.club.service.JornadaService;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.mapper.ReservaMapper;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.exception.PagoNoEncontradoException;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Anulación de un cobro cargado por error en el club ({@link Pago#anular}): el cobro no tendría que
 * existir. El ADMIN anula cualquiera (también las bonificaciones); el EMPLEADO, solo los cobros que
 * registró el personal dentro de la jornada en curso.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnulacionService {

    private final ReservaService reservaService;
    private final UsuarioService usuarioService;
    private final JornadaService jornadaService;
    private final EstadosDePago estados;
    private final ReservaMapper reservaMapper;
    private final Reloj reloj;

    @Transactional
    public ReservaResponse anular(Long reservaId, Long pagoId, String motivo, String email) {
        Usuario personal = usuarioService.autenticado(email);
        Reserva reserva = reservaService.bloquearComoPersonal(reservaId, personal);
        Pago pago = reserva.pago(pagoId).orElseThrow(() -> new PagoNoEncontradoException("Pago no encontrado: " + pagoId));
        if (pago.esBonificacion()) {
            Roles.exigirAdmin(personal, "Solo el ADMIN puede anular una bonificación.");
        } else if (!Roles.esAdmin(personal) && !pago.seRegistroEnLaJornada(jornadaService.actual())) {
            throw new PermisoDenegadoException("Solo podés anular cobros registrados en la jornada de hoy.");
        }
        pago.anular(personal, motivo.trim(), reloj.ahora());
        estados.recalcularTrasDeshacer(reserva);
        log.info("Cobro #{} ({}) de ${} de la reserva #{} anulado por {}: {}", pagoId, pago.getProvider(),
                pago.getMonto(), reservaId, email, pago.getMotivoAnulacion());
        return reservaMapper.aResponse(reserva);
    }
}
