package com.LosCiruelos.padel_club_api.Entities;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.PaymentProvider;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Pagos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id")
    private Reserva reserva;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPago estado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentProvider provider;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "external_payment_id")
    private String externalPaymentId;

    @Column(name = "preference_id")
    private String paymentToken;

    // Parte del pago que se devolvió (DEVUELTO) o que hay que devolver (A_REEMBOLSAR,
    // REEMBOLSO_PENDIENTE). null en esos estados significa el monto completo.
    @Column(name = "monto_reembolsado", precision = 10, scale = 2)
    private BigDecimal montoReembolsado;

    // Cuándo se pidió el reembolso que MP todavía no aceptó (REEMBOLSO_PENDIENTE):
    // pasado el plazo de reintentos queda A_REEMBOLSAR
    @Column(name = "reembolso_solicitado_at")
    private LocalDateTime reembolsoSolicitadoAt;

    // Cuándo pasó a DEVUELTO o A_REEMBOLSAR (cierre de caja: devoluciones del día)
    @Column(name = "reembolsado_at")
    private LocalDateTime reembolsadoAt;

    // Pagos registrados por el personal (en el club o bonificaciones): quién lo cargó
    @Column(name = "registrado_por_id")
    private Long registradoPorId;

    // Motivo de una bonificación (opcional), ej. "socio", "torneo", "cortesía"
    @Column(length = 200)
    private String motivo;

    // Partido: qué jugador pagó (o a quién se le bonificó)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "jugador_id")
    private ReservaJugador jugador;

    // Identifica al pago ante Mercado Pago (claves de idempotencia). Se genera al crearlo y no cambia.
    @Builder.Default
    @Column(name = "clave_idempotencia", nullable = false, unique = true, updatable = false)
    private UUID claveIdempotencia = UUID.randomUUID();

    @PrePersist
    void asignarClaveIdempotencia() {
        if (claveIdempotencia == null) {
            claveIdempotencia = UUID.randomUUID();
        }
    }

    /**
     * Estados en los que el pago movió (o puede estar moviendo) dinero: cobrado, devuelto, por
     * devolver o en revisión. PENDIENTE, RECHAZADO y EXPIRADO no cuentan: nunca se cobraron.
     */
    public static final Set<EstadoPago> ESTADOS_CON_DINERO = Set.of(EstadoPago.APROBADO, EstadoPago.DEVUELTO,
            EstadoPago.A_REEMBOLSAR, EstadoPago.REEMBOLSO_PENDIENTE, EstadoPago.EN_PROCESO);

    /** Una bonificación cubre parte del turno sin que el cliente pague: no es plata. */
    public boolean esBonificacion() {
        return provider == PaymentProvider.BONIFICACION;
    }

    /** Lo que el club se queda de este pago: lo aprobado menos lo devuelto o a devolver. */
    public BigDecimal getMontoRetenido() {
        return switch (estado) {
            case APROBADO -> montoReembolsado == null ? monto : monto.subtract(montoReembolsado);
            case DEVUELTO, A_REEMBOLSAR, REEMBOLSO_PENDIENTE ->
                montoReembolsado == null ? BigDecimal.ZERO : monto.subtract(montoReembolsado);
            default -> BigDecimal.ZERO;
        };
    }
}