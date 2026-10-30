package com.LosCiruelos.padel_club_api.reservas.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Turno;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.TipoReserva;

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
import jakarta.persistence.OrderBy;
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
    // (CONFIRMADA: partido del ADMIN sin nada pagado, que igual está confirmado)
    public static final Set<EstadoReserva> ESTADOS_CONFIRMADOS = Set.of(EstadoReserva.CONFIRMADA,
            EstadoReserva.SEÑADA, EstadoReserva.PARCIALMENTE_PAGADA, EstadoReserva.PAGADA);

    // Estados de una reserva que se juega (o se jugó): se entregan los productos alquilados
    public static final Set<EstadoReserva> ESTADOS_QUE_SE_JUEGAN = Set.of(EstadoReserva.CONFIRMADA,
            EstadoReserva.SEÑADA, EstadoReserva.PARCIALMENTE_PAGADA, EstadoReserva.PAGADA,
            EstadoReserva.COMPLETADA);

    // Confirmadas que todavía deben plata (el turno pudo haberse jugado o no)
    public static final Set<EstadoReserva> ESTADOS_CON_SALDO = Set.of(EstadoReserva.CONFIRMADA,
            EstadoReserva.SEÑADA, EstadoReserva.PARCIALMENTE_PAGADA);

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

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoReserva tipo = TipoReserva.NORMAL;

    // Partido creado por un turno fijo (null en el resto)
    @ManyToOne
    @JoinColumn(name = "turno_fijo_id")
    private TurnoFijo turnoFijo;

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

    // Partido: los 4 jugadores (vacío en una reserva NORMAL)
    @Builder.Default
    @BatchSize(size = 50)
    @OrderBy("posicion")
    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservaJugador> jugadores = new ArrayList<>();

    public boolean esPartido() {
        return tipo == TipoReserva.PARTIDO;
    }

    /**
     * Parte del jugador en esa posición (1 a 4): el total dividido entre los 4, redondeado para
     * abajo a centavos; los centavos que sobran van al último.
     */
    public BigDecimal parteDe(int posicion) {
        BigDecimal parte = montoTotal.divide(BigDecimal.valueOf(Jugadores.POR_PARTIDO), 2, RoundingMode.DOWN);
        if (posicion < Jugadores.POR_PARTIDO) {
            return parte;
        }
        return montoTotal.subtract(parte.multiply(BigDecimal.valueOf(Jugadores.POR_PARTIDO - 1)));
    }

    /** Pagos (y bonificaciones) registrados a nombre del jugador. */
    private java.util.stream.Stream<Pago> pagosDe(ReservaJugador jugador) {
        return pagos.stream().filter(p -> p.getJugador() != null && p.getJugador().getId() != null
                && p.getJugador().getId().equals(jugador.getId()));
    }

    /** Plata que pagó el jugador (sin bonificaciones, descontado lo devuelto). */
    public BigDecimal pagadoPor(ReservaJugador jugador) {
        return Dinero.sumar(pagosDe(jugador).filter(p -> !p.esBonificacion()).map(Pago::getMontoRetenido));
    }

    /** Lo que se le bonificó al jugador. */
    public BigDecimal bonificadoA(ReservaJugador jugador) {
        return Dinero.sumar(pagosDe(jugador).filter(Pago::esBonificacion).map(Pago::getMontoRetenido));
    }

    /**
     * Lo que le falta al jugador de su parte, sin pasarse de lo que falta en la reserva. Si la
     * reserva ya está cubierta (por ejemplo, otro pagó todo), nadie debe nada.
     */
    public BigDecimal debeDe(ReservaJugador jugador) {
        if (getMontoPendiente().signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return parteDe(jugador.getPosicion()).subtract(pagadoPor(jugador)).subtract(bonificadoA(jugador))
                .max(BigDecimal.ZERO).min(getMontoPendiente());
    }

    /**
     * La puede operar (pagar, cancelar, modificar): su cliente o el personal. Si no puede, se
     * responde "no encontrada" para no revelar ids ajenos.
     */
    public boolean puedeOperarla(Usuario usuario) {
        return Roles.esPersonal(usuario) || cliente.getId().equals(usuario.getId());
    }

    /** La puede ver: quien la puede operar, o un jugador del partido. */
    public boolean puedeVerla(Usuario usuario) {
        return puedeOperarla(usuario) || (esPartido() && esJugador(usuario));
    }

    public boolean esJugador(Usuario usuario) {
        return jugadores.stream().anyMatch(j -> j.getUsuario() != null && j.getUsuario().getId().equals(usuario.getId()));
    }

    /** Cantidad pedida del producto del sistema con ese código (0 si no se pidió). */
    public int cantidadDe(String codigoProducto) {
        return productos.stream()
                .filter(p -> codigoProducto.equals(p.getProducto().getCodigo()))
                .mapToInt(ReservaProducto::getCantidad)
                .sum();
    }

    @Transient
    public BigDecimal getMontoProductos() {
        return Dinero.sumar(productos.stream()
                .map(ReservaProducto::getSubtotal));
    }

    @Transient
    public BigDecimal getMontoCancha() {
        return montoTotal.subtract(getMontoProductos());
    }

    /**
     * Los pagos iniciados y nunca cobrados (PENDIENTE) pasan a EXPIRADO: ya no se pueden cobrar.
     * @return los que cambiaron, para guardarlos
     */
    public List<Pago> expirarPagosPendientes() {
        List<Pago> expirados = pagos.stream().filter(p -> p.getEstado() == EstadoPago.PENDIENTE).toList();
        expirados.forEach(p -> p.setEstado(EstadoPago.EXPIRADO));
        return expirados;
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
        return Dinero.sumar(pagos.stream()
                .filter(p -> !p.esBonificacion())
                .map(Pago::getMontoRetenido));
    }

    /** Lo que el ADMIN bonificó (no se le cobra al cliente). */
    @Transient
    public BigDecimal getMontoBonificado() {
        return Dinero.sumar(pagos.stream()
                .filter(Pago::esBonificacion)
                .map(Pago::getMontoRetenido));
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

    /**
     * El estado se deriva de lo cubierto (pagado + bonificado):
     * nada → PENDIENTE · menos que la seña → PARCIALMENTE_PAGADA · seña o más → SEÑADA ·
     * total → PAGADA (o COMPLETADA si el turno ya terminó, ej. el saldo se cobró en el club después
     * de jugar; si no, la completa el job al terminar).
     * Una reserva confirmada que queda sin nada cubierto (reembolso) pasa a CANCELADA. Un partido
     * sin nada pagado queda CONFIRMADA (no vence ni se cancela por eso).
     * CANCELADA, EXPIRADA y COMPLETADA no cambian.
     *
     * @return true si el estado cambió
     */
    public boolean recalcularEstado(BigDecimal senia, LocalDateTime ahora) {
        if (estaInactiva() || estado == EstadoReserva.COMPLETADA) {
            return false;
        }
        BigDecimal cubierto = getMontoCubierto();
        EstadoReserva nuevo;
        if (cubierto.signum() == 0 && esPartido()) {
            nuevo = EstadoReserva.CONFIRMADA;
        } else if (cubierto.signum() == 0) {
            nuevo = estado == EstadoReserva.PENDIENTE ? EstadoReserva.PENDIENTE : EstadoReserva.CANCELADA;
        } else if (cubierto.compareTo(montoTotal) >= 0) {
            nuevo = turnoTermino(ahora) ? EstadoReserva.COMPLETADA : EstadoReserva.PAGADA;
        } else if (cubierto.compareTo(senia) >= 0) {
            nuevo = EstadoReserva.SEÑADA;
        } else {
            nuevo = EstadoReserva.PARCIALMENTE_PAGADA;
        }
        if (nuevo == estado) {
            return false;
        }
        estado = nuevo;
        return true;
    }

    /** Fecha y horario de la reserva (duración, superposición, si empezó o terminó). */
    @Transient
    public Turno turno() {
        return new Turno(fechaReserva, horaInicio, horaFin);
    }

    public LocalDateTime inicioDelTurno() {
        return turno().inicioEnFecha();
    }

    public LocalDateTime finDelTurno() {
        return turno().finEnFecha();
    }

    public boolean turnoEmpezo(LocalDateTime ahora) {
        return turno().empezo(ahora);
    }

    public boolean turnoTermino(LocalDateTime ahora) {
        return turno().termino(ahora);
    }

    /** La reserva ya no cuenta: cancelada o expirada. */
    public boolean estaInactiva() {
        return estado == EstadoReserva.CANCELADA || estado == EstadoReserva.EXPIRADA;
    }
}
