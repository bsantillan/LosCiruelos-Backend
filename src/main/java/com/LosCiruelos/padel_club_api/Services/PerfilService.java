package com.LosCiruelos.padel_club_api.Services;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.DTOs.Requests.PerfilRequest;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PerfilResponse;
import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.Exceptions.UsuarioNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PerfilService {

    private final UsuarioService usuarioService;
    private final AuthService authService;
    private final ClienteProfileService clienteProfileService;

    private PerfilResponse buildPerfilResponse(Usuario usuario, ClienteProfile perfil) {
        PerfilResponse response = new PerfilResponse();
        response.setNombre(usuario.getNombre());
        response.setApellido(usuario.getApellido());
        response.setEmail(usuario.getEmail());
        response.setTelefono(usuario.getTelefono());
        if (perfil != null) {
            response.setCategoria(perfil.getCategoria());
            response.setPosicion(perfil.getPosicion());
        }
        return response;
    }

    public PerfilResponse getPerfil(String email) {
        Usuario usuario = usuarioService.findByEmailOrThrow(
                email,
                new CredencialesInvalidasException("Usuario no encontrado"));

        ClienteProfile perfil = null;
        if (usuario.getRol() == Role.CLIENTE) {
            perfil = clienteProfileService.findByUsuario(usuario);
        }

        return buildPerfilResponse(usuario, perfil);
    }

    public PerfilResponse updatePerfil(PerfilRequest per_rq, String email) {
        Usuario usuario = usuarioService.findByEmailOrThrow(
                email,
                new CredencialesInvalidasException("Usuario no encontrado"));
        if (per_rq.getPosicion() != null && usuario.getRol() != Role.CLIENTE) {
            throw new IllegalArgumentException("Solo los usuarios con rol CLIENTE pueden actualizar la posición");
        }
        
        usuario = usuarioService.updateUsuario(usuario, per_rq.getNombre(), per_rq.getApellido(), per_rq.getTelefono());

        ClienteProfile perfil = null;

        if (usuario.getRol() == Role.CLIENTE) {
            perfil = clienteProfileService.findByUsuario(usuario);

            if (perfil == null) {
                throw new UsuarioNotFoundException("Perfil de cliente no encontrado para el usuario: " + email);
            }

            perfil = clienteProfileService.updateClienteProfile(perfil, perfil.getCategoria(), per_rq.getPosicion());
        }
        return this.buildPerfilResponse(usuario, perfil);
    }

    public void desactivarPerfil(String email, String refreshToken) {
        Usuario usuario = usuarioService.findByEmailOrThrow(
                email,
                new CredencialesInvalidasException("Usuario no encontrado"));
        usuarioService.desactivarUsuario(usuario);
        authService.logout(refreshToken);
    }
}
