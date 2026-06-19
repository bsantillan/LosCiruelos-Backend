package com.LosCiruelos.padel_club_api.Services;

import java.time.LocalDateTime;

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
                .categoriaActualizadaAt(categoria != null ? LocalDateTime.now() : null)
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

    public ClienteProfile updateCategoria(ClienteProfile perfil, Categoria categoria) {
        perfil.setCategoria(categoria);
        return clienteProfileRepository.save(perfil);
    }

    public void eliminarPorUsuario(Usuario usuario) {
        clienteProfileRepository.findByUsuario(usuario)
                .ifPresent(clienteProfileRepository::delete);
    }

    public Boolean esPerfilCompleto(ClienteProfile clienteProfile) {
        if (clienteProfile == null)
            return false;

        Usuario usuario = clienteProfile.getUsuario();
        return usuario.getTelefono() != null
                && usuario.getNombre() != null
                && usuario.getApellido() != null
                && clienteProfile.getCategoria() != null
                && clienteProfile.getPosicion() != null;
    }
}