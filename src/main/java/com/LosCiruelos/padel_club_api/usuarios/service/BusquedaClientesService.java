package com.LosCiruelos.padel_club_api.usuarios.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.usuarios.dto.response.ClienteBusquedaResponse;
import com.LosCiruelos.padel_club_api.usuarios.entity.ClienteProfile;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.usuarios.repository.UsuarioRepository;
import com.LosCiruelos.padel_club_api.common.Busquedas;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/**
 * Búsqueda de clientes para el personal (ADMIN y EMPLEADO): para filtrar el listado de reservas
 * y para reservar a nombre de un cliente. Solo devuelve usuarios CLIENTE.
 */
@Service
@RequiredArgsConstructor
public class BusquedaClientesService {

    public static final int MINIMO_CARACTERES = 2;
    public static final int MAXIMO_RESULTADOS = 20;

    private final UsuarioRepository usuarioRepository;
    private final ClienteProfileService clienteProfileService;

    @Transactional(readOnly = true)
    public List<ClienteBusquedaResponse> buscar(String buscar, boolean soloActivos) {
        String texto = Busquedas.normalizar(buscar);
        if (texto == null || texto.length() < MINIMO_CARACTERES) {
            throw new ReservaException("Escribí al menos " + MINIMO_CARACTERES + " caracteres para buscar.");
        }
        Specification<Usuario> spec = (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            condiciones.add(cb.equal(root.get("rol"), Role.CLIENTE));
            if (soloActivos) {
                condiciones.add(cb.isTrue(root.get("enabled")));
            }
            condiciones.add(Busquedas.nombreApellidoOEmail(cb, root, texto));
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        PageRequest primeros = PageRequest.of(0, MAXIMO_RESULTADOS,
                Sort.by("apellido", "nombre", "id"));
        return usuarioRepository.findAll(spec, primeros).stream().map(this::aResponse).toList();
    }

    private ClienteBusquedaResponse aResponse(Usuario u) {
        ClienteProfile perfil = u.getClienteProfile();
        return new ClienteBusquedaResponse(u.getId(), u.getNombre(), u.getApellido(), u.getEmail(), u.getTelefono(),
                Boolean.TRUE.equals(u.getTelefonoVerificado()), perfil != null ? perfil.getCategoria() : null,
                perfil != null ? perfil.getPosicion() : null, clienteProfileService.esPerfilCompleto(perfil),
                Boolean.TRUE.equals(u.getEnabled()));
    }
}
