package com.LosCiruelos.padel_club_api.usuarios.service;

import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;

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

    private final Reloj reloj;

    private final ConfiguracionService configuracionService;

    /**
     * Categoría y posición con las que se da de alta el cliente. El perfil ya lo tiene: se lo
     * crea {@code Usuario.asignarRol} al crear la cuenta.
     */
    public void cargarDatosDeJugador(Usuario cliente, Categoria categoria, Posicion posicion) {
        cliente.getClienteProfile().cargarDatosDeJugador(categoria, posicion, reloj.ahora());
    }

    public ClienteProfile findByUsuario(Usuario user) {
        return clienteProfileRepository.findByUsuario(user).orElse(null);
    }

    /**
     * Perfil completo según el rol: el CLIENTE además necesita su perfil de jugador; el ADMIN,
     * nombre, apellido y celular verificado (le llegan los avisos del club). El resto, siempre. El
     * celular del CLIENTE no se verifica si el club no le manda WhatsApp.
     */
    public boolean perfilCompleto(Usuario usuario) {
        boolean whatsappParaClientes = configuracionService.obtener().whatsappParaClientes();
        return switch (usuario.getRol()) {
            case CLIENTE -> {
                ClienteProfile perfil = findByUsuario(usuario);
                yield perfil != null && perfil.estaCompleto(whatsappParaClientes);
            }
            case ADMIN -> usuario.datosPersonalesCompletos(whatsappParaClientes);
            default -> true;
        };
    }
}
