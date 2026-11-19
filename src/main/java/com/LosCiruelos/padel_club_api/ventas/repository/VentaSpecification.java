package com.LosCiruelos.padel_club_api.ventas.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.LosCiruelos.padel_club_api.common.Busquedas;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.VentaItem;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Filtros combinables del listado de ventas del personal. */
public final class VentaSpecification {

    private VentaSpecification() {
    }

    public static Specification<Venta> delListado(FiltroVentas filtro) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro.desde() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filtro.desde()));
            }
            if (filtro.hasta() != null) {
                condiciones.add(cb.lessThan(root.get("createdAt"), filtro.hasta()));
            }
            if (filtro.medios() != null) {
                condiciones.add(filtro.medios().isEmpty() ? cb.disjunction() : root.get("medio").in(filtro.medios()));
            }
            if (filtro.estados() != null) {
                condiciones.add(filtro.estados().isEmpty() ? cb.disjunction() : root.get("estado").in(filtro.estados()));
            }
            if (filtro.clienteId() != null) {
                condiciones.add(cb.equal(root.get("cliente").get("id"), filtro.clienteId()));
            }
            if (filtro.textoCliente() != null) {
                condiciones.add(Busquedas.nombreApellidoOEmail(cb, root.join("cliente"), filtro.textoCliente()));
            }
            if (filtro.productoId() != null) {
                Subquery<Long> conProducto = query.subquery(Long.class);
                Root<VentaItem> item = conProducto.from(VentaItem.class);
                conProducto.select(item.get("id")).where(
                        cb.equal(item.get("venta"), root),
                        cb.equal(item.get("producto").get("id"), filtro.productoId()));
                condiciones.add(cb.exists(conProducto));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }
}
