package com.LosCiruelos.padel_club_api.Services;

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
                .build());
    }

    public ClienteProfile findByUsuario(Usuario user) {
        return clienteProfileRepository.findByUsuario(user).orElse(null);
    }

    public ClienteProfile updateClienteProfile(ClienteProfile perfil, Categoria categoria, Posicion posicion) {
        perfil.setCategoria(categoria);
        perfil.setPosicion(posicion);
        return clienteProfileRepository.save(perfil);
    }
}