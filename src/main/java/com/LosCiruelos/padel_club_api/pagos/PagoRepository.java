package com.LosCiruelos.padel_club_api.pagos;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;


public interface PagoRepository extends JpaRepository<Pago, Long> {

    // Para buscar el pago cuando llega el webhook de MP
    Optional<Pago> findByExternalPaymentId(String externalPaymentId);

    // Solo ids: el webhook bloquea primero la reserva y después lee el pago actualizado
    @Query("SELECT p.reserva.id FROM Pago p WHERE p.externalPaymentId = :externalPaymentId")
    Optional<Long> findReservaIdByExternalPaymentId(@Param("externalPaymentId") String externalPaymentId);

    @Query("SELECT p.reserva.id FROM Pago p WHERE p.id = :pagoId")
    Optional<Long> findReservaIdByPagoId(@Param("pagoId") Long pagoId);

    // Solo ids: el job bloquea cada reserva antes de tocar el pago
    @Query("SELECT p.id FROM Pago p WHERE p.estado = :estado ORDER BY p.id")
    List<Long> findIdsByEstado(@Param("estado") EstadoPago estado);

    // Para buscar los pagos de una reserva
    List<Pago> findByReservaId(Long reservaId);
}
