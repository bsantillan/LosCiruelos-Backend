package com.LosCiruelos.padel_club_api.usuarios.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.usuarios.entity.ClienteProfile;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Posicion;
import com.LosCiruelos.padel_club_api.usuarios.repository.ClienteProfileRepository;

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

    public void eliminarPorUsuario(Usuario usuario) {
        clienteProfileRepository.findByUsuario(usuario)
                .ifPresent(clienteProfileRepository::delete);
    }

    /**
     * Perfil completo según el rol: el CLIENTE además necesita su perfil de jugador; el ADMIN,
     * nombre, apellido y celular verificado (le llegan los avisos del club). El resto, siempre.
     */
    public boolean perfilCompleto(Usuario usuario) {
        return switch (usuario.getRol()) {
            case CLIENTE -> {
                ClienteProfile perfil = findByUsuario(usuario);
                yield perfil != null && perfil.estaCompleto();
            }
            case ADMIN -> usuario.datosPersonalesCompletos();
            default -> true;
        };
    }
}
