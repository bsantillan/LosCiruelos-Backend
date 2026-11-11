package com.LosCiruelos.padel_club_api.pagos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.CanalDePago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.OrdenQr;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;

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

    // Cobro con el QR de la caja: la order de Mercado Pago y hasta cuándo se puede pagar
    @Column(name = "order_id", length = 64)
    private String orderId;

    @Column(name = "vence_at")
    private LocalDateTime venceAt;

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

    // Pago con Cuenta DNI: el ADMIN comprobó en la cuenta del club que la plata entró (null: sin verificar)
    @Column(name = "verificado_at")
    private LocalDateTime verificadoAt;

    @Column(name = "verificado_por_id")
    private Long verificadoPorId;

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

    /**
     * Cambia el estado y registra cuándo entró la plata a caja (un cobro de MP que queda devuelto o
     * por devolver sin pasar por APROBADO también entró) y cuándo se devolvió.
     */
    public void cambiarEstado(EstadoPago nuevo, LocalDateTime ahora) {
        if (paidAt == null && nuevo != EstadoPago.EN_PROCESO && ESTADOS_CON_DINERO.contains(nuevo)) {
            paidAt = ahora;
        }
        if (nuevo == EstadoPago.DEVUELTO || nuevo == EstadoPago.A_REEMBOLSAR) {
            reembolsadoAt = ahora;
        }
        estado = nuevo;
    }

    /** Devueltos o por devolver: el club se queda con lo que no se devolvió (monto − montoReembolsado). */
    public static final Set<EstadoPago> ESTADOS_DEVUELTOS = Set.of(EstadoPago.DEVUELTO, EstadoPago.A_REEMBOLSAR,
            EstadoPago.REEMBOLSO_PENDIENTE);

    /** Los que alguna vez se cobraron: entran a caja el día del cobro aunque después se devuelvan. */
    public static final Set<EstadoPago> ESTADOS_COBRADOS = Set.of(EstadoPago.APROBADO, EstadoPago.DEVUELTO,
            EstadoPago.A_REEMBOLSAR, EstadoPago.REEMBOLSO_PENDIENTE);

    /** Una bonificación cubre parte del turno sin que el cliente pague: no es plata. */
    /**
     * Marca (o desmarca) un pago con Cuenta DNI o por transferencia como verificado: el ADMIN comprobó
     * en la cuenta del club que la plata entró. Los otros medios no se verifican.
     */
    public void verificar(boolean verificado, Long adminId, LocalDateTime ahora) {
        if (!provider.seVerifica()) {
            throw new EstadoInvalidoException("Solo se verifican los pagos con Cuenta DNI o por transferencia.");
        }
        verificadoAt = verificado ? ahora : null;
        verificadoPorId = verificado ? adminId : null;
    }

    public boolean estaVerificado() {
        return verificadoAt != null;
    }

    /** Por dónde entró: los de Mercado Pago se separan en online, QR de la caja y transferencia. */
    public CanalDePago canal() {
        return switch (provider) {
            case MANUAL -> CanalDePago.EFECTIVO;
            case MERCADO_PAGO -> esCobroQr() ? CanalDePago.MERCADO_PAGO_QR : CanalDePago.MERCADO_PAGO_ONLINE;
            case TRANSFERENCIA_MP -> CanalDePago.TRANSFERENCIA_MP;
            case CUENTA_DNI -> CanalDePago.CUENTA_DNI;
            case BONIFICACION -> CanalDePago.BONIFICACION;
        };
    }

    public boolean esBonificacion() {
        return provider == PaymentProvider.BONIFICACION;
    }

    /** Lo que el club se queda de este pago: lo aprobado menos lo devuelto o a devolver. */
    /** Un cobro con el QR de la caja de Mercado Pago (tiene una order). */
    public boolean esCobroQr() {
        return orderId != null;
    }

    /** Lo que el proveedor del QR necesita de este cobro (la referencia lleva solo ids internos). */
    public OrdenQr ordenQr() {
        return new OrdenQr(orderId, "reserva-" + reserva.getId() + "-pago-" + id, claveIdempotencia.toString(),
                monto);
    }

    /** Un QR que todavía se puede pagar: esperando el pago y sin vencer. */
    public boolean esQrActivo(LocalDateTime ahora) {
        return esCobroQr() && estado == EstadoPago.EN_PROCESO && venceAt != null && venceAt.isAfter(ahora);
    }

    /** Se devuelve por la API del proveedor (un pago online o un QR); los demás, a mano. */
    public boolean seDevuelvePorApi() {
        return externalPaymentId != null || esCobroQr();
    }

    public BigDecimal getMontoRetenido() {
        return switch (estado) {
            case APROBADO -> montoReembolsado == null ? monto : monto.subtract(montoReembolsado);
            case DEVUELTO, A_REEMBOLSAR, REEMBOLSO_PENDIENTE ->
                montoReembolsado == null ? BigDecimal.ZERO : monto.subtract(montoReembolsado);
            default -> BigDecimal.ZERO;
        };
    }
}