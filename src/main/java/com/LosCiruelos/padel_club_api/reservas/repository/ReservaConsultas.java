package com.LosCiruelos.padel_club_api.reservas.repository;

import org.springframework.data.jpa.domain.Specification;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ListadoReservasResponse.Totales;

/**
 * Consultas de reservas que Spring Data no arma solo. Es un fragmento de {@link ReservaRepository}
 * (lo implementa {@link ReservaConsultasImpl}), así que se usa como reservaRepository.totalesParaPersonal(…).
 */
public interface ReservaConsultas {

    /**
     * Totales del listado del personal sobre el filtro completo: cantidad de reservas, suma de sus
     * montos, lo cobrado (aprobado menos lo devuelto) en efectivo y con Mercado Pago, lo bonificado y
     * lo que deben las que tienen saldo.
     */
    Totales totalesParaPersonal(FiltroReservas filtro);

    /** La primera y la última fecha de turno de las reservas que cumplen {@code spec} (MIN/MAX, con índice). */
    com.LosCiruelos.padel_club_api.common.RangoDeFechas rangoDeFechas(Specification<Reserva> spec);
}
