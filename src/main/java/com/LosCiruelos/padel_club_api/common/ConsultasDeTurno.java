package com.LosCiruelos.padel_club_api.common;

import java.time.LocalDateTime;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Las condiciones de horario de las consultas de reservas, escritas una sola vez. Los fragmentos
 * JPQL usan el alias {@code r} para la reserva y los parámetros {@code :horaInicio},
 * {@code :horaFin}, {@code :hoy} y {@code :hora}. Son las mismas reglas que {@link Turno}.
 */
public final class ConsultasDeTurno {

    private ConsultasDeTurno() {
    }

    /** La reserva se superpone con [:horaInicio, :horaFin) (la fecha se filtra aparte). */
    public static final String SE_SUPERPONE = "r.horaInicio < :horaFin AND r.horaFin > :horaInicio";

    /** El turno ya terminó: fecha anterior a hoy, o hoy con la hora de fin ya pasada. */
    public static final String TERMINADO = "(r.fechaReserva < :hoy OR (r.fechaReserva = :hoy AND r.horaFin <= :hora))";

    /** El turno ya empezó: fecha anterior a hoy, o hoy con la hora de inicio ya pasada. */
    public static final String EMPEZO = "(r.fechaReserva < :hoy OR (r.fechaReserva = :hoy AND r.horaInicio <= :hora))";

    /** Criteria: el turno de la reserva todavía no terminó. */
    public static Predicate noTermino(CriteriaBuilder cb, Path<?> reserva, LocalDateTime ahora) {
        return cb.or(
                cb.greaterThan(reserva.get("fechaReserva"), ahora.toLocalDate()),
                cb.and(cb.equal(reserva.get("fechaReserva"), ahora.toLocalDate()),
                        cb.greaterThan(reserva.get("horaFin"), ahora.toLocalTime())));
    }
}
