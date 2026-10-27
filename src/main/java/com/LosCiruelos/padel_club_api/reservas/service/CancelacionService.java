package com.LosCiruelos.padel_club_api.reservas.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.Services.UsuarioService;
import com.LosCiruelos.padel_club_api.pagos.service.PagoService;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Services.Avisos.AvisoReserva;
import com.LosCiruelos.padel_club_api.Services.Avisos.TipoAviso;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.OpcionReembolso;
import com.LosCiruelos.padel_club_api.mapper.ReservaMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cancelación de reservas con reembolso (reglas en {@link PoliticaCancelacion}).
 *
 * Todo pasa en una transacción con la reserva bloqueada. Si Mercado Pago no acepta un
 * reembolso (ej. el pago todavía se está acreditando) la reserva se cancela igual y el pago
 * queda REEMBOLSO_PENDIENTE: el job de reembolsos lo reintenta.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CancelacionService {

    private final ReservaService reservaService;
    private final ReservaMapper reservaMapper;
    private final PagoService pagoService;
    private final PoliticaCancelacion politica;
    private final UsuarioService usuarioService;
    private final Reloj reloj;
    private final ApplicationEventPublisher eventos;

    /**
     * @param opcionPersonal solo para ADMIN o EMPLEADO (obligatoria si la reserva tiene dinero
     *                       cobrado): qué se devuelve. El cliente no la elige: se aplica la política.
     */
    @Transactional
    public ReservaResponse cancelar(Long id, String email, OpcionReembolso opcionPersonal) {
        Usuario usuario = usuarioService.autenticado(email);
        boolean esPersonal = Roles.esPersonal(usuario);
        if (opcionPersonal != null && !esPersonal) {
            throw new ReservaException("Solo el personal del club puede elegir el reembolso.");
        }

        Reserva reserva = reservaService.bloquearConAcceso(id, usuario);
        if (reserva.esPartido() && usuario.getRol() != Role.ADMIN) {
            throw new PermisoDenegadoException("Solo el ADMIN puede cancelar un partido.");
        }
        return cancelarBloqueada(reserva, usuario, esPersonal, opcionPersonal);
    }

    /** Cancela una reserva ya bloqueada (también la usan las bajas de los turnos fijos). */
    ReservaResponse cancelarBloqueada(Reserva reserva, Usuario usuario, boolean esPersonal,
            OpcionReembolso opcionPersonal) {
        Long id = reserva.getId();
        String email = usuario.getEmail();
        LocalDateTime ahora = reloj.ahora();
        EstadoReserva estado = reserva.getEstado();

        if (estado != EstadoReserva.PENDIENTE && !Reserva.ESTADOS_CONFIRMADOS.contains(estado)) {
            throw new EstadoInvalidoException("No se puede cancelar una reserva " + estado + ".");
        }
        if (reserva.tienePagoEnProceso()) {
            throw new EstadoInvalidoException(
                    "La reserva tiene un pago en proceso; esperá a que Mercado Pago lo resuelva.");
        }

        BigDecimal reembolso;
        if (esPersonal) {
            if (reserva.turnoTermino(ahora)) {
                throw new EstadoInvalidoException("El turno ya terminó: la reserva no se puede cancelar.");
            }
            boolean hayDineroCobrado = reserva.getMontoPagado().signum() > 0;
            if (hayDineroCobrado && opcionPersonal == null) {
                throw new ReservaException("Indicá qué se devuelve: reembolso=TOTAL, SENIA o NADA.");
            }
            reembolso = hayDineroCobrado ? politica.montoSegun(reserva, opcionPersonal) : BigDecimal.ZERO;
        } else {
            PoliticaCancelacion.Evaluacion evaluacion = politica.evaluarParaCliente(reserva, ahora);
            if (!evaluacion.cancelable()) {
                throw new EstadoInvalidoException("El turno ya empezó: la reserva no se puede cancelar.");
            }
            reembolso = evaluacion.montoReembolso();
        }

        if (reembolso.signum() > 0) {
            pagoService.reembolsar(reserva, reembolso);
        }
        reservaService.cancelar(reserva);
        log.info("Reserva #{} cancelada por {} ({}): reembolso ${}", id, email, usuario.getRol(), reembolso);
        avisar(reserva, usuario, esPersonal, reembolso);
        return reservaMapper.aResponse(reserva);
    }

    /**
     * Aviso a los ADMIN (quién canceló y qué se devuelve) y, si canceló el personal, al cliente:
     * él no lo hizo y si no se le avisa no se entera.
     */
    private void avisar(Reserva reserva, Usuario quien, boolean esPersonal, BigDecimal reembolso) {
        if (reserva.esPartido()) {
            avisarJugadores(reserva, reembolso);
            return;
        }
        String detalle = "Canceló " + (esPersonal ? "el club (" + quien.getNombre() + ")" : "el cliente") + ". "
                + resultadoDelReembolso(reserva, reembolso);
        eventos.publishEvent(AvisoReserva.de(TipoAviso.RESERVA_CANCELADA, reserva, reembolso, detalle));

        if (esPersonal) {
            String paraCliente = reembolso.signum() > 0
                    ? "Te devolvemos " + Dinero.texto(reembolso) + ". Ante cualquier duda, comunicate con el club."
                    : "Ante cualquier duda, comunicate con el club.";
            eventos.publishEvent(AvisoReserva.de(TipoAviso.CANCELADA_POR_CLUB, reserva, reembolso, paraCliente));
        }
    }

    /** Partido (lo canceló el ADMIN): se avisa a cada jugador registrado. */
    private void avisarJugadores(Reserva reserva, BigDecimal reembolso) {
        String detalle = reembolso.signum() > 0
                ? "Lo que se pagó se devuelve según lo que indicó el club. Ante cualquier duda, comunicate con el club."
                : "Ante cualquier duda, comunicate con el club.";
        reserva.getJugadores().stream()
                .filter(j -> j.getUsuario() != null)
                .forEach(j -> eventos.publishEvent(AvisoReserva.para(TipoAviso.CANCELADA_POR_CLUB, reserva,
                        j.getUsuario(), reembolso, detalle)));
    }

    /**
     * Cómo salió el reembolso, para el aviso: hecho por Mercado Pago, pendiente (MP todavía no lo
     * aceptó; se reintenta solo), fallido (MP lo rechazó: hay que devolverlo a mano) o en el club.
     */
    static String resultadoDelReembolso(Reserva reserva, BigDecimal reembolso) {
        if (reembolso.signum() <= 0) {
            return "Sin reembolso.";
        }
        BigDecimal hecho = sumar(reserva, EstadoPago.DEVUELTO, true);
        BigDecimal pendiente = sumar(reserva, EstadoPago.REEMBOLSO_PENDIENTE, true);
        BigDecimal fallido = sumar(reserva, EstadoPago.A_REEMBOLSAR, true);
        BigDecimal enElClub = sumar(reserva, EstadoPago.A_REEMBOLSAR, false);

        StringBuilder texto = new StringBuilder("Reembolso de ").append(Dinero.texto(reembolso)).append(":");
        if (hecho.signum() > 0) {
            texto.append(" Mercado Pago devolvió ").append(Dinero.texto(hecho)).append(" (exitoso).");
        }
        if (pendiente.signum() > 0) {
            texto.append(" Mercado Pago todavía no aceptó ").append(Dinero.texto(pendiente))
                    .append(": se reintenta solo durante 24 h (pendiente).");
        }
        if (fallido.signum() > 0) {
            texto.append(" Mercado Pago rechazó ").append(Dinero.texto(fallido))
                    .append(": hay que devolverlo a mano (fallido).");
        }
        if (enElClub.signum() > 0) {
            texto.append(" Hay que devolver ").append(Dinero.texto(enElClub))
                    .append(" a mano (se cobró en el club).");
        }
        return texto.toString();
    }

    /** Suma lo que se devolvió o hay que devolver de los pagos en ese estado, online o en el club. */
    private static BigDecimal sumar(Reserva reserva, EstadoPago estado, boolean online) {
        return Dinero.sumar(reserva.getPagos().stream()
                .filter(p -> p.getEstado() == estado && (p.getExternalPaymentId() != null) == online)
                .map(p -> p.getMontoReembolsado() != null ? p.getMontoReembolsado() : p.getMonto()));
    }
}
