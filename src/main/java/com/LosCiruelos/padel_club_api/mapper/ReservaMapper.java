package com.LosCiruelos.padel_club_api.mapper;

import java.time.LocalDateTime;
import java.util.Comparator;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.reservas.dto.response.JugadorResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaProductoResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaJugador;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaProducto;
import com.LosCiruelos.padel_club_api.reservas.service.PoliticaCancelacion;

import lombok.RequiredArgsConstructor;

/**
 * Reserva → ReservaResponse. Arma la respuesta de una reserva: los datos de la entidad más lo que depende de la
 * configuración y de la hora (seña, si el cliente la puede cancelar y cuánto se le devolvería, si
 * el turno ya se jugó y debe saldo).
 */
@Component
@RequiredArgsConstructor
public class ReservaMapper {

    private final ConfiguracionService configuracionService;
    private final PoliticaCancelacion politicaCancelacion;
    private final Reloj reloj;
    private final PagoMapper pagoMapper;

    public ReservaResponse aResponse(Reserva reserva) {
        ReservaResponse response = datos(reserva);
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

    /** Los datos de la reserva tal como están guardados. */
    private ReservaResponse datos(Reserva reserva) {
        ReservaResponse response = new ReservaResponse();
        response.setId(reserva.getId());
        response.setCanchaId(reserva.getCancha().getId());
        response.setCanchaNumero(reserva.getCancha().getNumero());
        response.setFechaReserva(reserva.getFechaReserva());
        response.setHoraInicio(reserva.getHoraInicio());
        response.setHoraFin(reserva.getHoraFin());
        response.setMontoTotal(reserva.getMontoTotal());
        response.setMontoCancha(reserva.getMontoCancha());
        response.setMontoProductos(reserva.getMontoProductos());
        response.setMontoPagado(reserva.getMontoPagado());
        response.setMontoBonificado(reserva.getMontoBonificado());
        response.setMontoPendiente(reserva.getMontoPendiente());
        response.setCantidadPelotas(reserva.cantidadDe(Producto.CODIGO_PELOTAS));
        response.setCantidadPaletas(reserva.cantidadDe(Producto.CODIGO_PALETAS));
        response.setProductos(reserva.getProductos().stream().map(ReservaMapper::producto).toList());
        response.setEstado(reserva.getEstado());
        response.setTipo(reserva.getTipo());
        response.setTurnoFijoId(reserva.getTurnoFijo() != null ? reserva.getTurnoFijo().getId() : null);
        response.setJugadores(reserva.getJugadores().stream().map(j -> jugador(reserva, j)).toList());
        response.setClienteId(reserva.getCliente().getId());
        response.setClienteNombre(nombreCompleto(reserva.getCliente()));
        response.setCreatedAt(reserva.getCreatedAt());
        response.setExpiresAt(reserva.getExpiresAt());
        response.setPagos(reserva.getPagos().stream()
                .sorted(Comparator.comparing(Pago::getCreatedAt))
                .map(pagoMapper::aResponse)
                .toList());
        return response;
    }

    private static ReservaProductoResponse producto(ReservaProducto item) {
        ReservaProductoResponse r = new ReservaProductoResponse();
        r.setProductoId(item.getProducto().getId());
        r.setCodigo(item.getProducto().getCodigo());
        r.setNombre(item.getProducto().getNombre());
        r.setTipo(item.getProducto().getTipo());
        r.setCantidad(item.getCantidad());
        r.setPrecioUnitario(item.getPrecioUnitario());
        r.setSubtotal(item.getSubtotal());
        r.setCantidadDevuelta(item.getCantidadDevuelta());
        r.setDevueltaAt(item.getDevueltaAt());
        return r;
    }

    private static JugadorResponse jugador(Reserva reserva, ReservaJugador jugador) {
        return new JugadorResponse(jugador.getId(), jugador.getPosicion(),
                jugador.getUsuario() != null ? jugador.getUsuario().getId() : null, jugador.nombreParaMostrar(),
                jugador.getUsuario() != null ? jugador.getUsuario().getTelefono() : jugador.getTelefono(),
                jugador.esInvitado(), reserva.parteDe(jugador.getPosicion()), reserva.pagadoPor(jugador),
                reserva.bonificadoA(jugador), reserva.debeDe(jugador));
    }

    private static String nombreCompleto(Usuario cliente) {
        String nombre = cliente.getNombre();
        String apellido = cliente.getApellido();
        return ((nombre != null ? nombre : "") + " " + (apellido != null ? apellido : "")).trim();
    }
}
