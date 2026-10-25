package com.LosCiruelos.padel_club_api.Specifications;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.LosCiruelos.padel_club_api.DTOs.Admin.FiltroReservasAdmin;
import com.LosCiruelos.padel_club_api.Entities.Pago;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** Filtros de los listados de reservas ("Mis reservas" y el del personal). */
public final class ReservaSpecification {

    private ReservaSpecification() {
    }

    public static Specification<Reserva> delCliente(Usuario cliente) {
        return (root, query, cb) -> cb.equal(root.get("cliente"), cliente);
    }

    /**
     * Próximas: el turno todavía no terminó y la reserva ocupa el turno (confirmada, o PENDIENTE
     * en plazo o con un pago EN_PROCESO). Es el mismo criterio que usa la disponibilidad.
     */
    public static Specification<Reserva> proximas(LocalDateTime ahora) {
        return (root, query, cb) -> esProxima(root, query, cb, ahora);
    }

    /** Historial: todo lo que no es próxima (completadas, canceladas, vencidas y turnos ya jugados). */
    public static Specification<Reserva> historial(LocalDateTime ahora) {
        return (root, query, cb) -> cb.not(esProxima(root, query, cb, ahora));
    }

    public static Specification<Reserva> conEstados(Collection<EstadoReserva> estados) {
        return (root, query, cb) -> root.get("estado").in(estados);
    }

    /**
     * Excluye las reservas descartadas: CANCELADA o EXPIRADA sin ningún pago en
     * {@link Pago#ESTADOS_CON_DINERO} (aprobado, devuelto, por devolver o en revisión). No se
     * borran: quedan para auditoría y estadísticas, y se ven con incluirDescartadas=true.
     */
    public static Specification<Reserva> sinDescartadas() {
        return (root, query, cb) -> {
            Subquery<Long> conDinero = query.subquery(Long.class);
            Root<Pago> pago = conDinero.from(Pago.class);
            conDinero.select(pago.get("id")).where(
                    cb.equal(pago.get("reserva"), root),
                    pago.get("estado").in(Pago.ESTADOS_CON_DINERO));
            return cb.or(
                    cb.not(root.get("estado").in(EstadoReserva.CANCELADA, EstadoReserva.EXPIRADA)),
                    cb.exists(conDinero));
        };
    }

    /**
     * Listado del personal: combina todos los filtros que vengan (los null no filtran). A diferencia
     * de "Mis reservas", incluye las canceladas y vencidas sin pago.
     */
    public static Specification<Reserva> paraPersonal(FiltroReservasAdmin filtro) {
        return (root, query, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (filtro.desde() != null) {
                condiciones.add(cb.greaterThanOrEqualTo(root.get("fechaReserva"), filtro.desde()));
            }
            if (filtro.hasta() != null) {
                condiciones.add(cb.lessThanOrEqualTo(root.get("fechaReserva"), filtro.hasta()));
            }
            if (filtro.estados() != null) {
                condiciones.add(filtro.estados().isEmpty() ? cb.disjunction()
                        : root.get("estado").in(filtro.estados()));
            }
            if (filtro.clienteId() != null) {
                condiciones.add(cb.equal(root.get("cliente").get("id"), filtro.clienteId()));
            }
            if (filtro.canchaId() != null) {
                condiciones.add(cb.equal(root.get("cancha").get("id"), filtro.canchaId()));
            }
            if (filtro.textoCliente() != null) {
                condiciones.add(Busquedas.nombreApellidoOEmail(cb, root.join("cliente"), filtro.textoCliente()));
            }
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
    }

    public static Specification<Reserva> desde(LocalDate desde) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaReserva"), desde);
    }

    public static Specification<Reserva> hasta(LocalDate hasta) {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("fechaReserva"), hasta);
    }

    // Todas las comparaciones son sobre columnas NOT NULL o están protegidas con isNotNull,
    // así el NOT de "historial" nunca evalúa a null (y ninguna reserva queda fuera de los dos).
    private static Predicate esProxima(Root<Reserva> root, jakarta.persistence.criteria.CriteriaQuery<?> query,
            CriteriaBuilder cb, LocalDateTime ahora) {
        LocalDate hoy = ahora.toLocalDate();
        Predicate turnoNoTermino = cb.or(
                cb.greaterThan(root.get("fechaReserva"), hoy),
                cb.and(cb.equal(root.get("fechaReserva"), hoy),
                        cb.greaterThan(root.get("horaFin"), ahora.toLocalTime())));

        Predicate confirmada = root.get("estado").in(Reserva.ESTADOS_CONFIRMADOS);

        Subquery<Long> pagoEnProceso = query.subquery(Long.class);
        Root<Pago> pago = pagoEnProceso.from(Pago.class);
        pagoEnProceso.select(pago.get("id")).where(
                cb.equal(pago.get("reserva"), root),
                cb.equal(pago.get("estado"), EstadoPago.EN_PROCESO));

        Predicate pendienteQueOcupa = cb.and(
                cb.equal(root.get("estado"), EstadoReserva.PENDIENTE),
                cb.or(
                        cb.and(cb.isNotNull(root.get("expiresAt")), cb.greaterThan(root.get("expiresAt"), ahora)),
                        cb.exists(pagoEnProceso)));

        return cb.and(turnoNoTermino, cb.or(confirmada, pendienteQueOcupa));
    }
}
