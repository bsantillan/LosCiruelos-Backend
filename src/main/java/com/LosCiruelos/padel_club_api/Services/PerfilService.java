package com.LosCiruelos.padel_club_api.Services;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.DTOs.Requests.PerfilRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PerfilResponse;
import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.CategoriaException;
import com.LosCiruelos.padel_club_api.Exceptions.ValidationException;
import com.LosCiruelos.padel_club_api.Security.JWTUtil;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaConsultaService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final UsuarioService usuarioService;
    private final ClienteProfileService clienteProfileService;
    private final ReservaConsultaService reservaConsultaService;
    private final JWTUtil jwtUtil;
    private final Reloj reloj;

    @Transactional(readOnly = true)
    public PerfilResponse getPerfil(String email) {
        Usuario usuario = usuario(email);
        ClienteProfile perfil = usuario.getRol() == Role.CLIENTE ? clienteProfileService.findByUsuario(usuario) : null;
        return buildPerfilResponse(usuario, perfil);
    }

    /**
     * Valida todo antes de modificar nada y corre en una transacción: si algo falla,
     * no queda ningún dato a medio guardar.
     */
    @Transactional
    public PerfilResponse updatePerfil(PerfilRequest req, String email) {
        Usuario usuario = usuario(email);
        LocalDateTime ahora = reloj.ahora();
        ClienteProfile perfil = null;

        if (usuario.getRol() == Role.CLIENTE) {
            Map<String, String> errores = new LinkedHashMap<>();
            if (req.getPosicion() == null)
                errores.put("posicion", "La posición es obligatoria.");
            if (req.getCategoria() == null)
                errores.put("categoria", "La categoría es obligatoria.");
            if (!errores.isEmpty())
                throw new ValidationException(errores);

            perfil = clienteProfileService.findByUsuario(usuario);
            if (perfil == null) {
                // cliente sin perfil de jugador (datos viejos): se crea vacío y se carga ahora
                perfil = clienteProfileService.crearClienteProfile(usuario, null, null);
            }

            // Solo se controla la categoría si efectivamente cambia
            if (req.getCategoria() != perfil.getCategoria()) {
                if (!clienteProfileService.puedeCambiarCategoria(perfil, ahora)) {
                    throw new CategoriaException(Boolean.TRUE.equals(perfil.getCategoriaModificada())
                            ? "Ya modificaste tu categoría. Contactá al club para realizar otro cambio."
                            : "El período para modificar tu categoría expiró. Contactá al club para realizar cambios.");
                }
                if (perfil.getCategoria() == null) {
                    // primera carga: empieza a correr el plazo de 24 hs
                    perfil.setCategoriaActualizadaAt(ahora);
                } else {
                    perfil.setCategoriaModificada(true);
                }
                perfil.setCategoria(req.getCategoria());
            }
            perfil.setPosicion(req.getPosicion());
        } else if (req.getPosicion() != null || req.getCategoria() != null) {
            throw new IllegalArgumentException("Solo los usuarios con rol CLIENTE tienen categoría y posición.");
        }

        usuario = usuarioService.updateUsuario(usuario, req.getNombre(), req.getApellido(), req.getTelefono());
        if (perfil != null) {
            perfil = clienteProfileService.save(perfil);
        }
        return buildPerfilResponse(usuario, perfil);
    }

    /** Desactiva la cuenta del cliente y cierra todas sus sesiones. */
    @Transactional
    public void desactivarPerfil(String email) {
        Usuario usuario = usuario(email);
        usuarioService.desactivarUsuario(usuario);
        jwtUtil.deleteAllRefreshTokens(usuario);
    }

    private PerfilResponse buildPerfilResponse(Usuario usuario, ClienteProfile perfil) {
        LocalDateTime ahora = reloj.ahora();
        PerfilResponse response = new PerfilResponse();
        response.setNombre(usuario.getNombre());
        response.setApellido(usuario.getApellido());
        response.setEmail(usuario.getEmail());
        response.setTelefono(usuario.getTelefono());
        response.setTelefonoVerificado(usuario.getTelefonoVerificado());
        response.setCanalNotificaciones(usuario.getCanalNotificaciones());
        response.setPerfilCompleto(clienteProfileService.perfilCompleto(usuario));

        response.setCantDiasMiembro((int) ChronoUnit.DAYS.between(usuario.getTermsAcceptedAt(), ahora));
        response.setCantPartidos((int) reservaConsultaService.contarPorEstado(usuario, EstadoReserva.COMPLETADA));
        response.setCantPartidosEsteMes(reservaConsultaService.countPartidosEsteMes(usuario.getId(), EstadoReserva.COMPLETADA));
        response.setDiasDesdeUltimoPartido(reservaConsultaService.ultimaReservaCompletada(usuario)
                .map(r -> Math.toIntExact(ChronoUnit.DAYS.between(r.getFechaReserva(), reloj.hoy())))
                .orElse(null));

        if (perfil != null) {
            response.setCategoria(perfil.getCategoria());
            response.setPosicion(perfil.getPosicion());
            response.setCategoriaActualizadaAt(perfil.getCategoriaActualizadaAt());
            response.setCategoriaEditable(clienteProfileService.puedeCambiarCategoria(perfil, ahora));
        }
        return response;
    }

    private Usuario usuario(String email) {
        return usuarioService.autenticado(email);
    }
}
