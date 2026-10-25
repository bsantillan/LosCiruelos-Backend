package com.LosCiruelos.padel_club_api.Entities;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Reservas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reserva {

    // Estados en los que la reserva ya tiene dinero aprobado y el turno está confirmado
    public static final Set<EstadoReserva> ESTADOS_CONFIRMADOS = Set.of(
            EstadoReserva.SEÑADA, EstadoReserva.PARCIALMENTE_PAGADA, EstadoReserva.PAGADA);

    // Estados de una reserva que se juega (o se jugó): se entregan los productos alquilados
    public static final Set<EstadoReserva> ESTADOS_QUE_SE_JUEGAN = Set.of(
            EstadoReserva.SEÑADA, EstadoReserva.PARCIALMENTE_PAGADA, EstadoReserva.PAGADA,
            EstadoReserva.COMPLETADA);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(nullable = false, name = "cancha_id")
    private Cancha cancha;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "fecha_reserva", nullable = false)
    private LocalDate fechaReserva;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    // cancha + productos
    @Column(name = "monto_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal montoTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "estado")
    private EstadoReserva estado;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cliente_id")
    private Usuario cliente;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by_id")
    private Usuario createdBy;

    // Fin del plazo para pagar mientras la reserva está PENDIENTE
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    // Cuándo se le mandó al cliente el recordatorio del turno (null: todavía no)
    @Column(name = "recordatorio_enviado_at")
    private LocalDateTime recordatorioEnviadoAt;

    // BatchSize: al listar una página de reservas, los pagos y productos de todas se cargan
    // en una consulta por colección (no una por reserva)
    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "reserva")
    private List<Pago> pagos = new ArrayList<>();

    // productos pedidos (pelotas, paletas) con el precio del momento
    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservaProducto> productos = new ArrayList<>();

    /** Cantidad pedida del producto del sistema con ese código (0 si no se pidió). */
    public int cantidadDe(String codigoProducto) {
        return productos.stream()
                .filter(p -> codigoProducto.equals(p.getProducto().getCodigo()))
                .mapToInt(ReservaProducto::getCantidad)
                .sum();
    }

    @Transient
    public BigDecimal getMontoProductos() {
        return productos.stream()
                .map(ReservaProducto::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transient
    public BigDecimal getMontoCancha() {
        return montoTotal.subtract(getMontoProductos());
    }

    /** Agrega el pago a la relación en memoria para que los montos calculados lo incluyan. */
    public void agregarPago(Pago pago) {
        if (!pagos.contains(pago)) {
            pagos.add(pago);
        }
        pago.setReserva(this);
    }

    /** La plata que el club se quedó: aprobado menos lo devuelto, sin las bonificaciones. */
    @Transient
    public BigDecimal getMontoPagado() {
        return pagos.stream()
                .filter(p -> !p.esBonificacion())
                .map(Pago::getMontoRetenido)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Lo que el ADMIN bonificó (no se le cobra al cliente). */
    @Transient
    public BigDecimal getMontoBonificado() {
        return pagos.stream()
                .filter(Pago::esBonificacion)
                .map(Pago::getMontoRetenido)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Lo pagado más lo bonificado: define el estado de la reserva y lo que falta. */
    @Transient
    public BigDecimal getMontoCubierto() {
        return getMontoPagado().add(getMontoBonificado());
    }

    @Transient
    public BigDecimal getMontoPendiente() {
        return montoTotal.subtract(getMontoCubierto());
    }

    /** Tiene un pago cobrado o que MP todavía está procesando. */
    public boolean tienePagoActivo() {
        return pagos.stream().anyMatch(p -> p.getEstado() == EstadoPago.APROBADO
                || p.getEstado() == EstadoPago.EN_PROCESO);
    }

    public boolean tienePagoEnProceso() {
        return pagos.stream().anyMatch(p -> p.getEstado() == EstadoPago.EN_PROCESO);
    }

    /** PENDIENTE cuyo plazo para pagar ya pasó (el job todavía no la marcó EXPIRADA). */
    public boolean estaVencida(LocalDateTime ahora) {
        return estado == EstadoReserva.PENDIENTE && expiresAt != null && !expiresAt.isAfter(ahora);
    }

    /** PENDIENTE dentro del plazo para pagar. */
    public boolean estaPendienteVigente(LocalDateTime ahora) {
        return estado == EstadoReserva.PENDIENTE && !estaVencida(ahora);
    }

    /**
     * Puede recibir pagos: pendiente en plazo, o confirmada con saldo (también después de
     * jugar: una reserva solo pasa a COMPLETADA cuando está totalmente paga).
     */
    public boolean admitePagos(LocalDateTime ahora) {
        return estaPendienteVigente(ahora) || ESTADOS_CONFIRMADOS.contains(estado);
    }

    public LocalDateTime inicioDelTurno() {
        return fechaReserva.atTime(horaInicio);
    }

    public LocalDateTime finDelTurno() {
        return fechaReserva.atTime(horaFin);
    }

    public boolean turnoEmpezo(LocalDateTime ahora) {
        return !ahora.isBefore(inicioDelTurno());
    }

    public boolean turnoTermino(LocalDateTime ahora) {
        return !ahora.isBefore(finDelTurno());
    }

    /** La reserva ya no cuenta: cancelada o expirada. */
    public boolean estaInactiva() {
        return estado == EstadoReserva.CANCELADA || estado == EstadoReserva.EXPIRADA;
    }
}
