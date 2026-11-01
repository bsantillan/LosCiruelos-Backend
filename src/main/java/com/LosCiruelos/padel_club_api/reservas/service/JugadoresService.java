package com.LosCiruelos.padel_club_api.reservas.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.usuarios.exception.UsuarioNotFoundException;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.reservas.dto.request.JugadorRequest;
import com.LosCiruelos.padel_club_api.reservas.entity.Jugadores;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaJugador;

import lombok.RequiredArgsConstructor;

/**
 * Jugadores de una reserva: valida los que pide el ADMIN (clientes registrados o invitados) y los
 * asigna por posición. Lo usan los partidos y los turnos fijos; corre dentro de la transacción de
 * quien lo llama.
 */
@Service
@RequiredArgsConstructor
public class JugadoresService {

    static final String MENSAJE_RESPONSABLE =
            "El jugador 1 tiene que ser un cliente registrado: es el responsable del partido.";

    private final UsuarioService usuarioService;

    /** Un jugador ya validado: un cliente registrado o un invitado (nombre y teléfono opcional). */
    public record DatosJugador(Usuario usuario, String nombre, String telefono) {

        boolean mismaPersonaQue(Usuario otroUsuario, String otroNombre) {
            if (usuario != null || otroUsuario != null) {
                return usuario != null && otroUsuario != null && usuario.getId().equals(otroUsuario.getId());
            }
            return nombre.equalsIgnoreCase(otroNombre);
        }
    }

    /**
     * Valida los 4 jugadores: cada uno es un cliente registrado y activo (usuarioId) o un invitado
     * (nombre, y teléfono opcional). Un mismo cliente no puede estar dos veces. El jugador 1 tiene
     * que ser un cliente registrado: es el responsable (el cliente de la reserva).
     */
    public List<DatosJugador> resolverJugadores(List<JugadorRequest> pedidos) {
        if (pedidos == null || pedidos.size() != Jugadores.POR_PARTIDO) {
            throw new ReservaException("Un partido tiene " + Jugadores.POR_PARTIDO + " jugadores.");
        }
        List<DatosJugador> jugadores = new ArrayList<>();
        Set<Long> usuarios = new HashSet<>();
        for (int i = 0; i < pedidos.size(); i++) {
            JugadorRequest pedido = pedidos.get(i);
            int posicion = i + 1;
            if (pedido == null) {
                throw new ReservaException("Falta el jugador " + posicion + ".");
            }
            if (posicion == 1 && pedido.getUsuarioId() == null) {
                throw new ReservaException(MENSAJE_RESPONSABLE);
            }
            if (pedido.getUsuarioId() != null) {
                Usuario usuario;
                try {
                    usuario = usuarioService.findByIdOrThrow(pedido.getUsuarioId());
                } catch (UsuarioNotFoundException e) {
                    throw new ReservaException("El jugador " + posicion + " no existe (usuario "
                            + pedido.getUsuarioId() + ").");
                }
                if (usuario.getRol() != Role.CLIENTE) {
                    throw new ReservaException("El jugador " + posicion + " no es un cliente.");
                }
                if (!Boolean.TRUE.equals(usuario.getEnabled())) {
                    throw new ReservaException("La cuenta del jugador " + posicion + " está desactivada.");
                }
                if (!usuarios.add(usuario.getId())) {
                    throw new ReservaException("El mismo cliente no puede estar dos veces en el partido.");
                }
                jugadores.add(new DatosJugador(usuario, null, null));
            } else {
                String nombre = pedido.getNombre() != null ? pedido.getNombre().trim() : "";
                if (nombre.isEmpty()) {
                    throw new ReservaException("Indicá el cliente (usuarioId) o el nombre del jugador " + posicion
                            + ".");
                }
                String telefono = pedido.getTelefono() != null && !pedido.getTelefono().isBlank()
                        ? pedido.getTelefono().trim()
                        : null;
                jugadores.add(new DatosJugador(null, nombre, telefono));
            }
        }
        return jugadores;
    }

    /**
     * El responsable del partido: el jugador 1, un cliente registrado. Un turno fijo viejo con un
     * invitado en ese lugar no genera su partido hasta que se lo modifique.
     */
    static Usuario responsable(List<DatosJugador> jugadores) {
        Usuario responsable = jugadores.isEmpty() ? null : jugadores.get(0).usuario();
        if (responsable == null) {
            throw new ReservaException(MENSAJE_RESPONSABLE);
        }
        return responsable;
    }

    /** Los jugadores de una reserva nueva, en orden (posición 1 a 4). */
    static void asignar(Reserva reserva, List<DatosJugador> jugadores) {
        for (int i = 0; i < jugadores.size(); i++) {
            DatosJugador datos = jugadores.get(i);
            reserva.getJugadores().add(ReservaJugador.builder()
                    .reserva(reserva)
                    .posicion(i + 1)
                    .usuario(datos.usuario())
                    .nombre(datos.nombre())
                    .telefono(datos.telefono())
                    .build());
        }
    }

    /**
     * Los jugadores se actualizan en su lugar (posición por posición). Un jugador que ya pagó o
     * tiene una bonificación no se puede reemplazar: sus pagos quedarían a nombre de otro.
     */
    static void actualizar(Reserva reserva, List<DatosJugador> nuevos) {
        for (int i = 0; i < nuevos.size(); i++) {
            int posicion = i + 1;
            DatosJugador nuevo = nuevos.get(i);
            ReservaJugador actual = reserva.getJugadores().stream()
                    .filter(j -> j.getPosicion() == posicion)
                    .findFirst()
                    .orElse(null);
            if (actual == null) {
                reserva.getJugadores().add(ReservaJugador.builder().reserva(reserva).posicion(posicion)
                        .usuario(nuevo.usuario()).nombre(nuevo.nombre()).telefono(nuevo.telefono()).build());
                continue;
            }
            if (!nuevo.mismaPersonaQue(actual.getUsuario(), actual.getNombre()) && tienePagos(reserva, actual)) {
                throw new EstadoInvalidoException(actual.nombreParaMostrar()
                        + " ya pagó o tiene una bonificación: no se lo puede reemplazar.");
            }
            actual.setUsuario(nuevo.usuario());
            actual.setNombre(nuevo.nombre());
            actual.setTelefono(nuevo.telefono());
        }
    }

    private static boolean tienePagos(Reserva reserva, ReservaJugador jugador) {
        return reserva.getPagos().stream().anyMatch(p -> p.getJugador() != null
                && p.getJugador().getId().equals(jugador.getId())
                && Pago.ESTADOS_CON_DINERO.contains(p.getEstado()));
    }
}
