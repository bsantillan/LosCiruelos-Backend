package com.LosCiruelos.padel_club_api.pagos.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;


public interface PagoRepository extends JpaRepository<Pago, Long> {

    // Para buscar el pago cuando llega el webhook de MP
    Optional<Pago> findByExternalPaymentId(String externalPaymentId);

    /**
     * Pagos cobrados a mano que el ADMIN controla contra la cuenta (Cuenta DNI o transferencia) en
     * [inicio, fin), del más viejo al más nuevo. verificado null: todos.
     */
    @Query("""
            SELECT p FROM Pago p JOIN FETCH p.reserva
            WHERE p.provider IN :providers AND p.paidAt >= :inicio AND p.paidAt < :fin
              AND (:verificado IS NULL OR (:verificado = TRUE AND p.verificadoAt IS NOT NULL)
                   OR (:verificado = FALSE AND p.verificadoAt IS NULL))
            ORDER BY p.paidAt, p.id
            """)
    List<Pago> verificables(@Param("providers") java.util.Collection<PaymentProvider> providers,
            @Param("inicio") LocalDateTime inicio,
            @Param("fin") LocalDateTime fin, @Param("verificado") Boolean verificado);

    @Query("SELECT p.reserva.id FROM Pago p WHERE p.orderId = :orderId")
    Optional<Long> findReservaIdByOrderId(@Param("orderId") String orderId);

    /** Bloquea la caja del QR (una sola fila, común a reservas y ventas) hasta que termine la transacción. */
    @Query(value = "SELECT 1 FROM caja_qr WHERE id = 1 FOR UPDATE", nativeQuery = true)
    Integer bloquearCajaQr();

    /** Los cobros con el QR de la caja que esperan el pago (como mucho uno: la caja tiene un solo QR). */
    @Query("SELECT p FROM Pago p JOIN FETCH p.reserva WHERE p.venceAt IS NOT NULL AND p.estado = :enProceso")
    List<Pago> findQrEnCurso(@Param("enProceso") EstadoPago enProceso);

    /** Cobros con QR que siguen esperando el pago y ya vencieron (antes de :limite): los revisa el job. */
    @Query("SELECT p.id FROM Pago p WHERE p.orderId IS NOT NULL AND p.estado = :enProceso AND p.venceAt < :limite"
            + " ORDER BY p.id")
    List<Long> findIdsQrVencidos(@Param("enProceso") EstadoPago enProceso, @Param("limite") LocalDateTime limite);

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

    /** Las devoluciones en ese estado, con su pago, la reserva y el cliente, de la más vieja a la más nueva. */
    @Query("""
            SELECT d FROM Devolucion d JOIN FETCH d.pago p JOIN FETCH p.reserva r JOIN FETCH r.cliente
            WHERE d.estado = :estado
            ORDER BY d.createdAt, d.id
            """)
    List<com.LosCiruelos.padel_club_api.pagos.entity.Devolucion> findDevolucionesEnEstado(
            @Param("estado") com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoDevolucion estado);
}
