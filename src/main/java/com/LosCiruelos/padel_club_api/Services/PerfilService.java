package com.LosCiruelos.padel_club_api.Services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.DTOs.Requests.PerfilRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PerfilResponse;
import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.CategoriaException;
import com.LosCiruelos.padel_club_api.Exceptions.UsuarioNotFoundException;
import com.LosCiruelos.padel_club_api.Exceptions.ValidationException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final UsuarioService usuarioService;
    private final AuthService authService;
    private final ClienteProfileService clienteProfileService;
    private final ReservaService reservaService;

    private PerfilResponse buildPerfilResponse(Usuario usuario, ClienteProfile perfil) {
        PerfilResponse response = new PerfilResponse();
        response.setNombre(usuario.getNombre());
        response.setApellido(usuario.getApellido());
        response.setEmail(usuario.getEmail());
        response.setTelefono(usuario.getTelefono());
        response.setCantPartidos(reservaService.findByAll(usuario).size());

        response.setCantDiasMiembro((int) ChronoUnit.DAYS.between(usuario.getTermsAcceptedAt(), LocalDateTime.now()));
        response.setCantPartidosEsteMes(reservaService.countPartidosEsteMes(usuario.getId(), EstadoReserva.COMPLETADA));

        Optional<Reserva> ultimaReserva = reservaService.ultimaReservaCompletada(usuario, EstadoReserva.COMPLETADA);
        if (ultimaReserva.isPresent()) {
            int dias = Math.toIntExact(
                    ChronoUnit.DAYS.between(
                            ultimaReserva.get().getFechaReserva(),
                            LocalDate.now()));

            response.setDiasDesdeUltimoPartido(dias);
        } else {
            response.setDiasDesdeUltimoPartido(0);
        }

        if (perfil != null) {
            response.setCategoria(perfil.getCategoria());
            response.setPosicion(perfil.getPosicion());
            response.setCategoriaActualizadaAt(perfil.getCategoriaActualizadaAt());
        }
        return response;
    }

    public PerfilResponse getPerfil(String email) {
        Usuario usuario = usuarioService.findByEmail(email);

        ClienteProfile perfil = null;
        if (usuario.getRol() == Role.CLIENTE) {
            perfil = clienteProfileService.findByUsuario(usuario);
        }

        return buildPerfilResponse(usuario, perfil);
    }

    public PerfilResponse updatePerfil(PerfilRequest per_rq, String email) {
        Usuario usuario = usuarioService.findByEmail(email);

        if (usuario.getRol() == Role.CLIENTE) {
            Map<String, String> errores = new LinkedHashMap<>();
            if (per_rq.getPosicion() == null)
                errores.put("posicion", "La posición es obligatoria.");
            if (per_rq.getCategoria() == null)
                errores.put("categoria", "La categoría es obligatoria.");
            if (!errores.isEmpty())
                throw new ValidationException(errores);
        }

        if (per_rq.getPosicion() != null && usuario.getRol() != Role.CLIENTE) {
            throw new IllegalArgumentException("Solo los usuarios con rol CLIENTE pueden actualizar la posición.");
        }

        if (per_rq.getCategoria() != null && usuario.getRol() != Role.CLIENTE) {
            throw new IllegalArgumentException("Solo los usuarios con rol CLIENTE pueden actualizar la categoría.");
        }

        usuario = usuarioService.updateUsuario(usuario, per_rq.getNombre(), per_rq.getApellido(), per_rq.getTelefono());

        ClienteProfile perfil = null;

        if (usuario.getRol() == Role.CLIENTE) {
            perfil = clienteProfileService.findByUsuario(usuario);

            if (perfil == null) {
                throw new UsuarioNotFoundException("Perfil de cliente no encontrado para el usuario: " + email);
            }

            // ── Categoría ──
            if (perfil.getCategoriaActualizadaAt() != null &&
                    perfil.getCategoriaActualizadaAt().plusHours(24).isBefore(LocalDateTime.now())) {
                throw new CategoriaException(
                        "El período para modificar tu categoría expiró. Contactá al club para realizar cambios.");
            }

            LocalDateTime nuevaCategoriaActualizadaAt = perfil.getCategoriaActualizadaAt() == null
                    ? LocalDateTime.now()
                    : perfil.getCategoriaActualizadaAt(); // no pisamos si ya tiene fecha

            perfil = clienteProfileService.updateClienteProfile(perfil, per_rq.getCategoria(), per_rq.getPosicion(),
                    nuevaCategoriaActualizadaAt);
        }

        return this.buildPerfilResponse(usuario, perfil);
    }

    public void desactivarPerfil(String email, String refreshToken) {
        Usuario usuario = usuarioService.findByEmail(email);
        usuarioService.desactivarUsuario(usuario);
        authService.logout(refreshToken);
    }
}
