package com.LosCiruelos.padel_club_api.pagos.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.club.service.JornadaService;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.mapper.ReservaMapper;
import com.LosCiruelos.padel_club_api.pagos.dto.request.DevolucionRequest;
import com.LosCiruelos.padel_club_api.pagos.entity.Devolucion;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.PagoImputacion;
import com.LosCiruelos.padel_club_api.pagos.exception.DevolucionNoEncontradaException;
import com.LosCiruelos.padel_club_api.pagos.exception.PagoNoEncontradoException;
import com.LosCiruelos.padel_club_api.pagos.exception.PagoProveedorException;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Devoluciones de un cobro, línea por línea ({@link Pago#devolver}): la plata entró y se le devuelve
 * al cliente (solo el ADMIN). Lo cobrado por Mercado Pago (online o QR) se devuelve en el momento por
 * su API: si no lo acepta, 502 y no se registra nada. Lo cobrado en el club queda A_REEMBOLSAR hasta
 * que el personal lo entrega.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DevolucionService {

    private final ReservaService reservaService;
    private final UsuarioService usuarioService;
    private final JornadaService jornadaService;
    private final PagoRepository pagoRepository;
    private final EstadosDePago estados;
    private final DevolucionesPorApi porApi;
    private final ReservaMapper reservaMapper;
    private final Reloj reloj;

    @Transactional
    public ReservaResponse devolver(Long reservaId, Long pagoId, DevolucionRequest req, String email) {
        Usuario admin = usuarioService.autenticado(email);
        Roles.exigirAdmin(admin, "Solo el ADMIN puede devolver un cobro.");
        Reserva reserva = reservaService.bloquearConAcceso(reservaId, admin);
        Pago pago = reserva.pago(pagoId).orElseThrow(() -> new PagoNoEncontradoException("Pago no encontrado: " + pagoId));
        LocalDateTime ahora = reloj.ahora();
        List<PagoImputacion.Pedido> lineas = req.getLineas().stream()
                .map(l -> new PagoImputacion.Pedido(l.getConcepto(), l.getLineaId(), l.getMonto()))
                .toList();

        Devolucion devolucion = pago.devolver(lineas, req.getMotivo(), admin, ahora);
        if (pago.seDevuelvePorApi()) {
            try {
                porApi.devolver(pago, devolucion.getMonto(), "devolucion-" + devolucion.getClave());
            } catch (PagoProveedorException e) { // también ReembolsoFallidoException
                log.error("Pago #{}: Mercado Pago no aceptó la devolución de ${}", pagoId, devolucion.getMonto());
                throw new PagoProveedorException("Mercado Pago no aceptó la devolución. Intentá de nuevo.");
            }
            devolucion.marcarDevuelta(null, ahora);
        }
        pago.actualizarEstadoPorDevoluciones(ahora);
        pagoRepository.save(pago);
        estados.recalcularTrasDeshacer(reserva);
        log.info("Cobro #{} de la reserva #{}: {} devuelve ${} ({})", pagoId, reservaId, email, devolucion.getMonto(),
                devolucion.getEstado());
        return reservaMapper.aResponse(reserva);
    }

    /**
     * El personal le dio al cliente una devolución A_REEMBOLSAR: queda DEVUELTO, con la fecha (cuenta en
     * la caja de ese momento) y quién. El ADMIN, cualquiera; el EMPLEADO, las de la jornada en curso.
     */
    @Transactional
    public ReservaResponse entregar(Long reservaId, Long devolucionId, String email) {
        Usuario personal = usuarioService.autenticado(email);
        Reserva reserva = reservaService.bloquearConAcceso(reservaId, personal);
        Devolucion devolucion = reserva.getPagos().stream()
                .flatMap(p -> p.devolucion(devolucionId).stream())
                .findFirst()
                .orElseThrow(() -> new DevolucionNoEncontradaException(devolucionId));
        if (!Roles.esAdmin(personal) && !jornadaService.actual().incluye(devolucion.getCreatedAt())) {
            throw new PermisoDenegadoException("Solo podés entregar devoluciones de la jornada de hoy.");
        }
        LocalDateTime ahora = reloj.ahora();
        devolucion.entregar(personal, ahora);
        devolucion.getPago().actualizarEstadoPorDevoluciones(ahora);
        log.info("Devolución #{} de ${} (reserva #{}) entregada por {}", devolucionId, devolucion.getMonto(), reservaId,
                email);
        return reservaMapper.aResponse(reserva);
    }
}
