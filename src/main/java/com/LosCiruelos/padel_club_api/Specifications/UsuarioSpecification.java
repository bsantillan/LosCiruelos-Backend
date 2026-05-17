package com.LosCiruelos.padel_club_api.Specifications;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.LosCiruelos.padel_club_api.DTOs.Requests.FiltroUsuarioRequest;
import com.LosCiruelos.padel_club_api.Entities.ClienteProfile;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;

public class UsuarioSpecification {

    public static Specification<Usuario> conFiltros(FiltroUsuarioRequest filtros) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            predicates.add(cb.notEqual(root.get("rol"), Role.ADMIN));

            // Filtro por rol
            if (filtros.getRol() != null) {
                predicates.add(cb.equal(root.get("rol"), filtros.getRol()));
            }

            // Filtro por estado
            if (filtros.getEnabled() != null) {
                predicates.add(cb.equal(root.get("enabled"), filtros.getEnabled()));
            }

            // Filtro por nombre o apellido
            if (filtros.getNombre() != null && !filtros.getNombre().isBlank()) {
                String pattern = "%" + filtros.getNombre().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), pattern),
                        cb.like(cb.lower(root.get("apellido")), pattern)));
            }

            // Filtro por email
            if (filtros.getEmail() != null && !filtros.getEmail().isBlank()) {
                String pattern = "%" + filtros.getEmail().toLowerCase() + "%";
                predicates.add(cb.like(cb.lower(root.get("email")), pattern));
            }

            if (Role.CLIENTE.equals(filtros.getRol()) &&
                    (filtros.getCategoria() != null || filtros.getPosicion() != null)) {
                Join<Usuario, ClienteProfile> perfil = root.join("clienteProfile", JoinType.INNER);

                if (filtros.getCategoria() != null) {
                    predicates.add(cb.equal(perfil.get("categoria"), filtros.getCategoria()));
                }

                if (filtros.getPosicion() != null) {
                    predicates.add(cb.equal(perfil.get("posicion"), filtros.getPosicion()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
