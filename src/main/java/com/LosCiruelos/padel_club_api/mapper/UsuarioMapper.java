package com.LosCiruelos.padel_club_api.mapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.usuarios.dto.response.ClienteBusquedaResponse;
import com.LosCiruelos.padel_club_api.usuarios.dto.response.LoginResponse;
import com.LosCiruelos.padel_club_api.usuarios.dto.response.PerfilResponse;
import com.LosCiruelos.padel_club_api.usuarios.dto.response.UsuarioAdminResponse;
import com.LosCiruelos.padel_club_api.usuarios.entity.ClienteProfile;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;

/** Usuario (y su perfil de jugador) → las respuestas de login, perfil, panel del ADMIN y búsqueda de clientes. */
@Component
public class UsuarioMapper {

    /** Lo que jugó el usuario (lo consulta el service en reservas). */
    public record PartidosDelJugador(int jugados, Long esteMes, Integer diasDesdeUltimo) {
    }

    public LoginResponse aLogin(Usuario usuario, boolean perfilCompleto, String accessToken, String refreshToken) {
        LoginResponse response = new LoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        response.setId(usuario.getId());
        response.setNombre(usuario.getNombre());
        response.setApellido(usuario.getApellido());
        response.setEmail(usuario.getEmail());
        response.setPerfilCompleto(perfilCompleto);
        response.setRol(usuario.getRol());
        return response;
    }

    /**
     * @param perfil                null si el usuario no es CLIENTE
     * @param verificacionDeCelular si el celular se verifica con el código de WhatsApp
     */
    public PerfilResponse aPerfil(Usuario usuario, ClienteProfile perfil, boolean perfilCompleto,
            boolean verificacionDeCelular, PartidosDelJugador partidos, LocalDateTime ahora) {
        PerfilResponse response = new PerfilResponse();
        response.setNombre(usuario.getNombre());
        response.setApellido(usuario.getApellido());
        response.setEmail(usuario.getEmail());
        response.setTelefono(usuario.getTelefono());
        response.setTelefonoVerificado(usuario.getTelefonoVerificado());
        response.setCanalNotificaciones(usuario.getCanalNotificaciones());
        response.setPerfilCompleto(perfilCompleto);
        response.setVerificacionDeCelular(verificacionDeCelular);
        response.setCantDiasMiembro((int) ChronoUnit.DAYS.between(usuario.getTermsAcceptedAt(), ahora));
        response.setCantPartidos(partidos.jugados());
        response.setCantPartidosEsteMes(partidos.esteMes());
        response.setDiasDesdeUltimoPartido(partidos.diasDesdeUltimo());
        if (perfil != null) {
            response.setCategoria(perfil.getCategoria());
            response.setPosicion(perfil.getPosicion());
            response.setCategoriaActualizadaAt(perfil.getCategoriaActualizadaAt());
            response.setCategoriaEditable(perfil.puedeCambiarCategoria(ahora));
        }
        return response;
    }

    /** @param perfil null si el usuario no es CLIENTE */
    public UsuarioAdminResponse aAdminResponse(Usuario usuario, ClienteProfile perfil) {
        UsuarioAdminResponse response = new UsuarioAdminResponse();
        response.setId(usuario.getId());
        response.setNombre(usuario.getNombre());
        response.setApellido(usuario.getApellido());
        response.setEmail(usuario.getEmail());
        response.setTelefono(usuario.getTelefono());
        response.setTelefonoVerificado(usuario.getTelefonoVerificado());
        response.setCanalNotificaciones(usuario.getCanalNotificaciones());
        response.setRol(usuario.getRol());
        response.setEnabled(usuario.getEnabled());
        response.setEmailVerificado(usuario.getEmailVerificado());
        response.setProvider(usuario.getProvider());
        if (perfil != null) {
            response.setCategoria(perfil.getCategoria());
            response.setPosicion(perfil.getPosicion());
        }
        return response;
    }

    /** @param whatsappParaClientes si el club manda WhatsApp a los clientes (si no, el celular no se verifica) */
    public ClienteBusquedaResponse aBusquedaResponse(Usuario usuario, boolean whatsappParaClientes) {
        ClienteProfile perfil = usuario.getClienteProfile();
        return new ClienteBusquedaResponse(usuario.getId(), usuario.getNombre(), usuario.getApellido(),
                usuario.getEmail(), usuario.getTelefono(), Boolean.TRUE.equals(usuario.getTelefonoVerificado()),
                perfil != null ? perfil.getCategoria() : null, perfil != null ? perfil.getPosicion() : null,
                perfil != null && perfil.estaCompleto(whatsappParaClientes), Boolean.TRUE.equals(usuario.getEnabled()));
    }
}
