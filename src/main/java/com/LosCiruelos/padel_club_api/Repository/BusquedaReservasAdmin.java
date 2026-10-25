package com.LosCiruelos.padel_club_api.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.DTOs.Admin.FiltroReservasAdmin;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ListadoReservasResponse.Totales;
import com.LosCiruelos.padel_club_api.Entities.Pago;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.PaymentProvider;
import com.LosCiruelos.padel_club_api.Specifications.ReservaSpecification;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/**
 * Listado de reservas del personal: la página (con cancha y cliente en la misma consulta) y los
 * totales del filtro completo, todo calculado en la base.
 */
@Component
public class BusquedaReservasAdmin {

    /** Las que deben plata: confirmadas que todavía no están pagas. */
    public static final Set<EstadoReserva> DEBEN = Set.of(EstadoReserva.SEÑADA, EstadoReserva.PARCIALMENTE_PAGADA);

    private static final Set<EstadoPago> DEVUELTOS = Set.of(EstadoPago.DEVUELTO, EstadoPago.A_REEMBOLSAR,
            EstadoPago.REEMBOLSO_PENDIENTE);

    private final ReservaRepository reservaRepository;

    @PersistenceContext
    private EntityManager em;

    public BusquedaReservasAdmin(ReservaRepository reservaRepository) {
        this.reservaRepository = reservaRepository;
    }

    public Page<Reserva> buscar(FiltroReservasAdmin filtro, Pageable pageable) {
        return reservaRepository.findAll(ReservaSpecification.paraPersonal(filtro), pageable);
    }

    /**
     * Totales del filtro completo: cantidad de reservas, suma de sus montos, lo cobrado (aprobado
     * menos lo devuelto) en efectivo y con Mercado Pago, y lo que deben las SEÑADA y PARCIALMENTE_PAGADA.
     */
    public Totales totales(FiltroReservasAdmin filtro) {
        Specification<Reserva> spec = ReservaSpecification.paraPersonal(filtro);
        CriteriaBuilder cb = em.getCriteriaBuilder();

        // 1) cantidad, monto total y monto total de las que deben
        CriteriaQuery<Tuple> q = cb.createTupleQuery();
        Root<Reserva> r = q.from(Reserva.class);
        Expression<BigDecimal> totalQueDebe = cb.<BigDecimal>selectCase()
                .when(r.get("estado").in(DEBEN), r.<BigDecimal>get("montoTotal"))
                .otherwise(BigDecimal.ZERO);
        q.multiselect(cb.count(r), cb.coalesce(cb.sum(r.<BigDecimal>get("montoTotal")), BigDecimal.ZERO),
                cb.coalesce(cb.sum(totalQueDebe), BigDecimal.ZERO))
                .where(spec.toPredicate(r, q, cb));
        Tuple reservas = em.createQuery(q).getSingleResult();

        // 2) lo cobrado (total y de las que deben), sobre los pagos de esas mismas reservas
        CriteriaQuery<Tuple> qp = cb.createTupleQuery();
        Root<Pago> p = qp.from(Pago.class);
        Subquery<Long> ids = qp.subquery(Long.class);
        Root<Reserva> rs = ids.from(Reserva.class);
        ids.select(rs.get("id")).where(spec.toPredicate(rs, qp, cb));
        Expression<BigDecimal> retenido = retenido(cb, p); // incluye bonificaciones: sirve para lo que falta
        Expression<BigDecimal> cobrado = cb.<BigDecimal>selectCase()
                .when(cb.notEqual(p.get("provider"), PaymentProvider.BONIFICACION), retenido)
                .otherwise(BigDecimal.ZERO);
        Expression<BigDecimal> bonificado = cb.<BigDecimal>selectCase()
                .when(cb.equal(p.get("provider"), PaymentProvider.BONIFICACION), retenido)
                .otherwise(BigDecimal.ZERO);
        Expression<BigDecimal> retenidoQueDebe = cb.<BigDecimal>selectCase()
                .when(p.get("reserva").get("estado").in(DEBEN), retenido)
                .otherwise(BigDecimal.ZERO);
        Expression<BigDecimal> retenidoEfectivo = cb.<BigDecimal>selectCase()
                .when(cb.equal(p.get("provider"), PaymentProvider.MANUAL), retenido)
                .otherwise(BigDecimal.ZERO);
        qp.multiselect(cb.coalesce(cb.sum(cobrado), BigDecimal.ZERO),
                cb.coalesce(cb.sum(retenidoQueDebe), BigDecimal.ZERO),
                cb.coalesce(cb.sum(retenidoEfectivo), BigDecimal.ZERO),
                cb.coalesce(cb.sum(bonificado), BigDecimal.ZERO))
                .where(p.get("reserva").get("id").in(ids));
        Tuple cobros = em.createQuery(qp).getSingleResult();

        BigDecimal montoCobrado = monto(cobros, 0);
        BigDecimal efectivo = monto(cobros, 2);
        // lo que falta: el total de las que deben menos lo cubierto (pagado + bonificado) de ellas
        BigDecimal pendiente = monto(reservas, 2).subtract(monto(cobros, 1));
        return new Totales(reservas.get(0, Long.class), pesos(monto(reservas, 1)), pesos(montoCobrado),
                pesos(efectivo), pesos(montoCobrado.subtract(efectivo)), pesos(monto(cobros, 3)), pesos(pendiente));
    }

    /** Lo que el club se queda de un pago, igual que Pago.getMontoRetenido. */
    private static Expression<BigDecimal> retenido(CriteriaBuilder cb, Root<Pago> p) {
        Path<BigDecimal> monto = p.get("monto");
        Path<BigDecimal> reembolsado = p.get("montoReembolsado");
        return cb.<BigDecimal>selectCase()
                .when(cb.equal(p.get("estado"), EstadoPago.APROBADO),
                        cb.diff(monto, cb.coalesce(reembolsado, BigDecimal.ZERO)))
                .when(p.get("estado").in(DEVUELTOS), cb.diff(monto, cb.coalesce(reembolsado, monto)))
                .otherwise(BigDecimal.ZERO);
    }

    /** La suma puede volver como BigDecimal o como otro Number según la base. */
    private static BigDecimal monto(Tuple tupla, int posicion) {
        Object valor = tupla.get(posicion);
        return valor instanceof BigDecimal bd ? bd : new BigDecimal(String.valueOf(valor));
    }

    private static BigDecimal pesos(BigDecimal monto) {
        return monto.setScale(2, RoundingMode.HALF_UP);
    }
}
