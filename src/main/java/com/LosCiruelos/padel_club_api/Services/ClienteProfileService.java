package com.LosCiruelos.padel_club_api.Services;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.Categoria;
import com.LosCiruelos.padel_club_api.Entities.Enum.Posicion;
import com.LosCiruelos.padel_club_api.Repository.ClienteProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ClienteProfileService {

    private final ClienteProfileRepository clienteProfileRepository;

    public ClienteProfile crearClienteProfile(Usuario user, Categoria categoria, Posicion posicion) {

        return clienteProfileRepository.save(ClienteProfile.builder()
                .categoria(categoria)
                .posicion(posicion)
                .usuario(user)
                .categoriaActualizadaAt(categoria != null ? LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS) : null)
                .build());
    }

    public ClienteProfile findByUsuario(Usuario user) {
        return clienteProfileRepository.findByUsuario(user).orElse(null);
    }

    public ClienteProfile updateClienteProfile(ClienteProfile perfil, Categoria categoria, Posicion posicion, LocalDateTime categoriaActualizadaAt) {
        perfil.setCategoria(categoria);
        perfil.setPosicion(posicion);
        perfil.setCategoriaActualizadaAt(categoriaActualizadaAt);
        return clienteProfileRepository.save(perfil);
    }

    public ClienteProfile save(ClienteProfile perfil) {
        return clienteProfileRepository.save(perfil);
    }

    /**
     * El cliente puede cambiar su categoría una sola vez y dentro de las 24 hs desde que la
     * cargó. La primera carga (por ejemplo, después de entrar con Google) no cuenta como cambio.
     */
    public boolean puedeCambiarCategoria(ClienteProfile perfil, LocalDateTime ahora) {
        if (perfil.getCategoria() == null) {
            return true;
        }
        if (Boolean.TRUE.equals(perfil.getCategoriaModificada())) {
            return false;
        }
        return perfil.getCategoriaActualizadaAt() == null
                || !ahora.isAfter(perfil.getCategoriaActualizadaAt().plusHours(24));
    }

    public void eliminarPorUsuario(Usuario usuario) {
        clienteProfileRepository.findByUsuario(usuario)
                .ifPresent(clienteProfileRepository::delete);
    }

    /** CLIENTE: datos personales, celular verificado, categoría y posición. */
    public Boolean esPerfilCompleto(ClienteProfile clienteProfile) {
        if (clienteProfile == null)
            return false;

        Usuario usuario = clienteProfile.getUsuario();
        return datosPersonalesCompletos(usuario)
                && clienteProfile.getCategoria() != null
                && clienteProfile.getPosicion() != null;
    }

    /**
     * Perfil completo según el rol: el CLIENTE además necesita su perfil de jugador; el ADMIN,
     * nombre, apellido y celular verificado (le llegan los avisos del club). El resto, siempre.
     */
    public boolean perfilCompleto(Usuario usuario) {
        return switch (usuario.getRol()) {
            case CLIENTE -> esPerfilCompleto(findByUsuario(usuario));
            case ADMIN -> datosPersonalesCompletos(usuario);
            default -> true;
        };
    }

    /** Nombre, apellido y celular verificado con el código de WhatsApp (igual que el mail). */
    static boolean datosPersonalesCompletos(Usuario usuario) {
        return usuario.getTelefono() != null
                && Boolean.TRUE.equals(usuario.getTelefonoVerificado())
                && usuario.getNombre() != null
                && usuario.getApellido() != null;
    }
}