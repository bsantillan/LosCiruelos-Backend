package com.LosCiruelos.padel_club_api.pagos.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.club.service.JornadaService;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Jornada;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CajaResponse;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CajaResponse.Movimiento;
import com.LosCiruelos.padel_club_api.pagos.entity.Devolucion;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoDevolucion;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.MedioDeCaja;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.TipoMovimientoCaja;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.VentaDevolucion;
import com.LosCiruelos.padel_club_api.ventas.service.VentaConsultaService;

import lombok.RequiredArgsConstructor;

/**
 * El cierre de caja de una jornada: lo cobrado (reservas y ventas sueltas, sin lo anulado), lo
 * devuelto, el efectivo que tiene que haber en la caja y cada movimiento. El ADMIN ve cualquier
 * jornada; el EMPLEADO, la que está en curso.
 */
@Service
@RequiredArgsConstructor
public class CajaService {

    private final PagoRepository pagoRepository;
    private final VentaConsultaService ventaConsultaService;
    private final UsuarioService usuarioService;
    private final JornadaService jornadaService;

    /** @param fecha la jornada (null: la que está en curso) */
    @Transactional(readOnly = true)
    public CajaResponse cierre(LocalDate fecha, String email) {
        Usuario personal = usuarioService.autenticado(email);
        Jornada actual = jornadaService.actual();
        if (fecha != null && !Roles.esAdmin(personal) && !fecha.equals(actual.fecha())) {
            throw new PermisoDenegadoException("Solo podés ver la caja de la jornada en curso.");
        }
        Jornada jornada = fecha == null ? actual : jornadaService.del(fecha);
        Map<Long, String> nombres = new HashMap<>();
        List<Movimiento> movimientos = new ArrayList<>();
        BigDecimal bonificado = BigDecimal.ZERO;

        for (Pago pago : pagoRepository.findParaLaCaja(jornada.inicio(), jornada.fin(), Pago.ESTADOS_PARA_LA_CAJA)) {
            if (pago.esBonificacion()) {
                if (pago.getEstado() == EstadoPago.APROBADO) {
                    bonificado = bonificado.add(pago.getMonto());
                }
                continue;
            }
            MedioDeCaja medio = MedioDeCaja.de(pago.getProvider());
            Reserva reserva = pago.getReserva();
            String detalle = "Reserva #" + reserva.getId() + " · " + nombre(reserva.getCliente());
            movimientos.add(new Movimiento(pago.getPaidAt(), TipoMovimientoCaja.COBRO, medio, pago.getMonto(), detalle,
                    reserva.getId(), null, nombre(pago.getRegistradoPorId(), nombres)));
            if (pago.estaAnulado()) {
                movimientos.add(new Movimiento(pago.getAnuladoAt(), TipoMovimientoCaja.ANULACION, medio,
                        pago.getMonto().negate(), "Anulación · " + detalle + " · " + pago.getMotivoAnulacion(),
                        reserva.getId(), null, nombre(pago.getAnuladoPor())));
            }
        }
        for (Devolucion d : pagoRepository.findDevolucionesEntregadas(EstadoDevolucion.DEVUELTO, jornada.inicio(),
                jornada.fin())) {
            Reserva reserva = d.getPago().getReserva();
            movimientos.add(new Movimiento(d.getEntregadoAt(), TipoMovimientoCaja.DEVOLUCION,
                    MedioDeCaja.de(d.getPago().getProvider()), d.getMonto().negate(),
                    "Devolución · Reserva #" + reserva.getId() + " · " + nombre(reserva.getCliente()), reserva.getId(),
                    null, nombre(d.getEntregadoPor() != null ? d.getEntregadoPor() : d.getRegistradaPor())));
        }
        for (Venta venta : ventaConsultaService.paraLaCaja(jornada.inicio(), jornada.fin())) {
            MedioDeCaja medio = MedioDeCaja.de(venta.getMedio());
            String detalle = "Venta #" + venta.getId() + (venta.getCliente() != null ? " · " + nombre(venta.getCliente()) : "");
            movimientos.add(new Movimiento(venta.getCreatedAt(), TipoMovimientoCaja.VENTA, medio, venta.getTotal(),
                    detalle, null, venta.getId(), nombre(venta.getRegistradoPor())));
            if (venta.estaAnulada()) {
                movimientos.add(new Movimiento(venta.getAnuladaAt(), TipoMovimientoCaja.ANULACION, medio,
                        venta.getTotal().negate(), "Anulación · " + detalle + " · " + venta.getMotivoAnulacion(), null,
                        venta.getId(), nombre(venta.getAnuladaPor())));
            }
        }
        for (VentaDevolucion d : ventaConsultaService.devolucionesParaLaCaja(jornada.inicio(), jornada.fin())) {
            Venta venta = d.getVenta();
            movimientos.add(new Movimiento(d.getCreatedAt(), TipoMovimientoCaja.DEVOLUCION, MedioDeCaja.de(venta.getMedio()),
                    d.getMonto().negate(), "Devolución · Venta #" + venta.getId(), null, venta.getId(),
                    nombre(d.getRegistradaPor())));
        }

        List<Devolucion> aEntregar = pagoRepository.findDevolucionesCreadas(EstadoDevolucion.A_REEMBOLSAR,
                jornada.inicio(), jornada.fin());
        return CierreDeCaja.armar(jornada.fecha(), jornada.inicio(), jornada.fin(), movimientos, bonificado,
                aEntregar.size(), Dinero.sumar(aEntregar.stream().map(Devolucion::getMonto)));
    }

    private String nombre(Long usuarioId, Map<Long, String> nombres) {
        if (usuarioId == null) {
            return null;
        }
        return nombres.computeIfAbsent(usuarioId, id -> nombre(usuarioService.findByIdOrThrow(id)));
    }

    private static String nombre(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        String nombre = usuario.getNombre() != null ? usuario.getNombre() : "";
        String apellido = usuario.getApellido() != null ? usuario.getApellido() : "";
        String completo = (nombre + " " + apellido).trim();
        return completo.isEmpty() ? usuario.getEmail() : completo;
    }
}
