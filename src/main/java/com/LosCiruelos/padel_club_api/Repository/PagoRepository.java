package com.LosCiruelos.padel_club_api.Repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.LosCiruelos.padel_club_api.Entities.Pago;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    // Para buscar el pago cuando llega el webhook de MP
    Optional<Pago> findByExternalPaymentId(String externalPaymentId);

    // Para buscar el pago de una reserva
    List<Pago> findByReservaId(Long reservaId);
}