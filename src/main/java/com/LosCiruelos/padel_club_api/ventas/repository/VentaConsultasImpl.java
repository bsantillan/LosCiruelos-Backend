package com.LosCiruelos.padel_club_api.ventas.repository;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.data.jpa.domain.Specification;

import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.VentaDevolucion;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;

/** Implementación de {@link VentaConsultas}: los totales se calculan en la base con Criteria. */
public class VentaConsultasImpl implements VentaConsultas {

    @PersistenceContext
    private EntityManager em;

    @Override
    public TotalesDelListado totalesDelListado(FiltroVentas filtro) {
        Specification<Venta> spec = VentaSpecification.delListado(filtro);
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // 1) cantidad y total por medio y estado
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Venta> v = q.from(Venta.class);
        q.multiselect(v.get("medio"), v.get("estado"), cb.count(v),
                cb.coalesce(cb.sum(v.<BigDecimal>get("total")), BigDecimal.ZERO))
                .where(spec.toPredicate(v, q, cb))
                .groupBy(v.get("medio"), v.get("estado"));
        long cobradas = 0;
        long anuladas = 0;
        Map<MedioDePago, BigDecimal> cobrado = new EnumMap<>(MedioDePago.class);
        for (Tuple t : em.createQuery(q).getResultList()) {
            MedioDePago medio = t.get(0, MedioDePago.class);
            EstadoVenta estado = t.get(1, EstadoVenta.class);
            long cantidad = t.get(2, Long.class);
            if (estado == EstadoVenta.COBRADA) {
                cobradas += cantidad;
                cobrado.merge(medio, t.get(3, BigDecimal.class), BigDecimal::add);
            } else if (estado == EstadoVenta.ANULADA) {
                anuladas += cantidad;
            }
        }

        // 2) lo devuelto de las ventas cobradas del filtro, por medio
        CriteriaQuery<Tuple> qd = cb.createTupleQuery();
        Root<Venta> vd = qd.from(Venta.class);
        Join<Venta, VentaDevolucion> d = vd.join("devoluciones");
        qd.multiselect(vd.get("medio"), cb.coalesce(cb.sum(d.<BigDecimal>get("monto")), BigDecimal.ZERO))
                .where(cb.and(spec.toPredicate(vd, qd, cb), cb.equal(vd.get("estado"), EstadoVenta.COBRADA)))
                .groupBy(vd.get("medio"));
        Map<MedioDePago, BigDecimal> devuelto = new EnumMap<>(MedioDePago.class);
        for (Tuple t : em.createQuery(qd).getResultList()) {
            devuelto.put(t.get(0, MedioDePago.class), t.get(1, BigDecimal.class));
        }
        return new TotalesDelListado(cobradas, anuladas, cobrado, devuelto);
    }
}
