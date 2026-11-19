package com.LosCiruelos.padel_club_api.reservas.entity;

import java.util.TreeMap;
import java.util.Map;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.LosCiruelos.padel_club_api.club.entity.Cancha;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.PagoImputacion;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.ConceptoImputacion;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.reservas.exception.LineaCobradaException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.stream.Stream;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Turno;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.ModalidadPago;
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

    // Reserva que cargó el personal: cómo se paga (null en las del cliente y en los partidos)
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad_pago", length = 20)
    private ModalidadPago modalidadPago;

    // Cuándo y quién la canceló (null en las de antes de V43; quién null: el sistema)
    @Column(name = "cancelada_at")
    private LocalDateTime canceladaAt;

    @ManyToOne(fetch = jakarta.persistence.FetchType.LAZY)
    @JoinColumn(name = "cancelada_por_id")
    private Usuario canceladaPor;

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

    // Partido: de 1 a 4 jugadores (vacío en una reserva NORMAL)
    @Builder.Default
    @BatchSize(size = 50)
    @OrderBy("posicion")
    @OneToMany(mappedBy = "reserva", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservaJugador> jugadores = new ArrayList<>();

    /** Está confirmada aunque no tenga nada pagado: un partido o una reserva SIN_SENIA. */
    public boolean confirmadaSinPagar() {
        return esPartido() || modalidadPago == ModalidadPago.SIN_SENIA;
    }

    public boolean esPartido() {
        return tipo == TipoReserva.PARTIDO;
    }

    /**
     * La puede operar (pagar, cancelar, modificar): su cliente o el personal. Si no puede, se
     * responde "no encontrada" para no revelar ids ajenos.
     */
    public boolean puedeOperarla(Usuario usuario) {
        return Roles.esPersonal(usuario) || cliente.getId().equals(usuario.getId());
    }

    /**
     * El EMPLEADO solo ve y opera las reservas de la jornada en curso en adelante (fecha del turno;
     * pasada la medianoche, la jornada sigue siendo la del día anterior); las anteriores son del ADMIN.
     */
    public boolean quedaFueraDelEmpleado(Usuario usuario, LocalDate fechaDeLaJornada) {
        return Roles.esEmpleado(usuario) && fechaReserva.isBefore(fechaDeLaJornada);
    }

    /** @throws PermisoDenegadoException (403) si es un EMPLEADO y la reserva es de un día anterior */
    public void exigirQueLaPuedaOperar(Usuario personal, LocalDate fechaDeLaJornada) {
        if (quedaFueraDelEmpleado(personal, fechaDeLaJornada)) {
            throw new PermisoDenegadoException("Solo el ADMIN puede operar reservas de días anteriores.");
        }
    }

    /** La puede ver: quien la puede operar, o un jugador del partido. */
    public boolean puedeVerla(Usuario usuario) {
        return puedeOperarla(usuario) || (esPartido() && esJugador(usuario));
    }

    public boolean esJugador(Usuario usuario) {
        return jugadores.stream().anyMatch(j -> j.getUsuario() != null && j.getUsuario().getId().equals(usuario.getId()));
    }

    /** Lo pedido: id del producto → cantidad. */
    public Map<Long, Integer> pedidoDeProductos() {
        Map<Long, Integer> pedido = new TreeMap<>();
        productos.forEach(i -> pedido.put(i.getProducto().getId(), i.getCantidad()));
        return pedido;
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

    // ── Imputación de los pagos: qué cubre cada uno ──────────────────────

    /** Las imputaciones de los pagos cobrados (lo que cubre cada uno, sin lo devuelto). */
    private Stream<PagoImputacion> imputacionesVigentes() {
        return pagos.stream().flatMap(p -> p.imputacionesVigentes().stream());
    }

    /** Lo cobrado (y bonificado) que cubre la cancha. */
    @Transient
    public BigDecimal getMontoCanchaPagado() {
        return Dinero.sumar(imputacionesVigentes().filter(PagoImputacion::esDeLaCancha).map(PagoImputacion::getMonto));
    }

    /** Lo que falta cubrir de la cancha (nunca negativo). */
    @Transient
    public BigDecimal getMontoCanchaPendiente() {
        return getMontoCancha().subtract(getMontoCanchaPagado()).max(BigDecimal.ZERO);
    }

    /** Lo cobrado (y bonificado) que cubre esa línea de producto. */
    public BigDecimal pagadoDe(ReservaProducto linea) {
        return Dinero.sumar(imputacionesVigentes().filter(i -> i.esDe(linea)).map(PagoImputacion::getMonto));
    }

    /** Lo que falta cubrir de esa línea (nunca negativo). */
    public BigDecimal pendienteDe(ReservaProducto linea) {
        return linea.getSubtotal().subtract(pagadoDe(linea)).max(BigDecimal.ZERO);
    }

    /** Las líneas de producto en orden de creación. */
    private List<ReservaProducto> lineasEnOrden() {
        return productos.stream()
                .sorted(Comparator.comparing(ReservaProducto::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    /**
     * Imputación automática de {@code monto} (pagos online del cliente y cobros sin imputaciones):
     * primero la cancha hasta completarla y después cada línea de producto en orden de creación.
     * Si sobrara algo (datos viejos inconsistentes), va a la cancha.
     */
    public List<PagoImputacion> imputacionAutomatica(BigDecimal monto) {
        List<PagoImputacion> partes = new ArrayList<>();
        BigDecimal restante = monto;
        BigDecimal aCancha = restante.min(getMontoCanchaPendiente());
        restante = restante.subtract(aCancha);
        for (ReservaProducto linea : lineasEnOrden()) {
            BigDecimal aLinea = restante.min(pendienteDe(linea));
            if (aLinea.signum() > 0) {
                partes.add(PagoImputacion.aLinea(linea, aLinea));
                restante = restante.subtract(aLinea);
            }
        }
        aCancha = aCancha.add(restante);
        if (aCancha.signum() > 0) {
            partes.addFirst(PagoImputacion.aCancha(aCancha));
        }
        return partes;
    }

    /**
     * Valida lo que el personal indicó que cubre un cobro de {@code monto} (400 si no cierra):
     * la suma es el monto, cada parte es mayor a cero y no supera lo pendiente de su línea, la línea es
     * de esta reserva y no se repite. Sin imputaciones (null o vacía), se imputa en forma automática.
     */
    public List<PagoImputacion> imputacionPedida(List<PagoImputacion.Pedido> pedidas, BigDecimal monto) {
        if (pedidas == null || pedidas.isEmpty()) {
            return imputacionAutomatica(monto);
        }
        if (pedidas.stream().anyMatch(p -> p.monto() == null || p.monto().signum() <= 0)) {
            throw new ReservaException("El monto de cada imputación tiene que ser mayor a cero.");
        }
        BigDecimal suma = Dinero.sumar(pedidas.stream().map(PagoImputacion.Pedido::monto));
        if (suma.compareTo(monto) != 0) {
            throw new ReservaException("La suma de las imputaciones (%s) tiene que ser igual al monto (%s)."
                    .formatted(Dinero.texto(suma), Dinero.texto(monto)));
        }
        List<PagoImputacion> partes = new ArrayList<>();
        Set<Long> lineasVistas = new HashSet<>();
        boolean canchaVista = false;
        for (PagoImputacion.Pedido pedida : pedidas) {
            if (pedida.concepto() == ConceptoImputacion.CANCHA) {
                if (pedida.lineaId() != null) {
                    throw new ReservaException("Una imputación a la cancha no lleva lineaId.");
                }
                if (canchaVista) {
                    throw new ReservaException("La cancha está repetida en las imputaciones.");
                }
                canchaVista = true;
                validarQueNoSuperaLoPendiente("la cancha", pedida.monto(), getMontoCanchaPendiente());
                partes.add(PagoImputacion.aCancha(pedida.monto()));
                continue;
            }
            if (pedida.lineaId() == null) {
                throw new ReservaException("Indicá la línea (lineaId) de cada imputación a un producto.");
            }
            if (!lineasVistas.add(pedida.lineaId())) {
                throw new ReservaException("La línea #" + pedida.lineaId() + " está repetida en las imputaciones.");
            }
            ReservaProducto linea = productos.stream()
                    .filter(l -> pedida.lineaId().equals(l.getId()))
                    .findFirst()
                    .orElseThrow(() -> new ReservaException("La línea #" + pedida.lineaId() + " no es de esta reserva."));
            validarQueNoSuperaLoPendiente(linea.getProducto().getNombre(), pedida.monto(), pendienteDe(linea));
            partes.add(PagoImputacion.aLinea(linea, pedida.monto()));
        }
        return partes;
    }

    private static void validarQueNoSuperaLoPendiente(String nombre, BigDecimal monto, BigDecimal pendiente) {
        if (monto.compareTo(pendiente) > 0) {
            throw new ReservaException("Lo imputado a %s (%s) supera lo pendiente de esa línea (%s)."
                    .formatted(nombre, Dinero.texto(monto), Dinero.texto(pendiente)));
        }
    }

    /**
     * Lo cobrado no se saca: ninguna línea (ni la cancha, con su precio nuevo {@code montoCancha})
     * puede quedar por debajo de lo que tiene cubierto, y una línea cubierta no se puede sacar. Lo que
     * no tiene nada cobrado se saca libremente. Nada se reimputa solo de una línea a otra.
     *
     * @throws LineaCobradaException (409) con la línea y lo cobrado, para anular o devolver ese cobro antes
     */
    public void validarQueNoSacaLoCobrado(BigDecimal montoCancha) {
        BigDecimal cancha = getMontoCanchaPagado();
        if (montoCancha.compareTo(cancha) < 0) {
            throw new LineaCobradaException("La cancha", null, cancha, true);
        }
        Map<ReservaProducto, BigDecimal> porLinea = new LinkedHashMap<>();
        imputacionesVigentes().filter(i -> !i.esDeLaCancha())
                .forEach(i -> porLinea.merge(i.getLinea(), i.getMonto(), BigDecimal::add));
        porLinea.forEach((linea, cobrado) -> {
            if (!productos.contains(linea) || linea.getSubtotal().compareTo(cobrado) < 0) {
                throw new LineaCobradaException(linea.getProducto().getNombre(), linea.getId(), cobrado, false);
            }
        });
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
     * no tiene seña: sin nada pagado queda CONFIRMADA (no vence ni se cancela por eso), con algo
     * PARCIALMENTE_PAGADA y entero PAGADA (o COMPLETADA). Una reserva SIN_SENIA (la cargó el ADMIN
     * para cobrarla en el club) sin nada pagado también queda CONFIRMADA, igual que una que quedó
     * CONFIRMADA al anular o devolver su cobro ({@link #recalcularTrasDeshacerUnCobro}).
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
        if (cubierto.signum() == 0 && (confirmadaSinPagar() || estado == EstadoReserva.CONFIRMADA)) {
            // una CONFIRMADA sin nada cubierto (un partido, SIN_SENIA o porque se anuló o devolvió su
            // cobro) sigue confirmada: no se cancela ni vuelve a PENDIENTE por un cambio de total
            nuevo = EstadoReserva.CONFIRMADA;
        } else if (cubierto.signum() == 0) {
            nuevo = estado == EstadoReserva.PENDIENTE ? EstadoReserva.PENDIENTE : EstadoReserva.CANCELADA;
        } else if (cubierto.compareTo(montoTotal) >= 0) {
            nuevo = turnoTermino(ahora) ? EstadoReserva.COMPLETADA : EstadoReserva.PAGADA;
        } else if (cubierto.compareTo(senia) >= 0 && !esPartido()) {
            // un partido no tiene seña: hasta pagarse entero queda PARCIALMENTE_PAGADA
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

    /**
     * Cambió el total (productos que suma o saca el personal): el estado se recalcula con lo
     * cubierto. Una COMPLETADA que queda con saldo vuelve a estar abierta (deuda) hasta que se cobre.
     */
    public void recalcularPorCambioDeTotal(BigDecimal senia, LocalDateTime ahora) {
        if (estado == EstadoReserva.COMPLETADA && getMontoPendiente().signum() > 0) {
            estado = EstadoReserva.PAGADA;
        }
        recalcularEstado(senia, ahora);
    }

    /**
     * Después de anular o devolver un cobro: el estado se recalcula como siempre, pero una reserva que
     * ya estaba confirmada y queda sin nada cubierto queda CONFIRMADA: nunca vuelve a PENDIENTE ni
     * vence (ni se cancela) por deshacer un cobro. Una COMPLETADA que queda con saldo vuelve a abrirse.
     *
     * @return true si el estado cambió
     */
    public boolean recalcularTrasDeshacerUnCobro(BigDecimal senia, LocalDateTime ahora) {
        EstadoReserva anterior = estado;
        if (estado == EstadoReserva.COMPLETADA && getMontoPendiente().signum() > 0) {
            estado = EstadoReserva.PAGADA;
        }
        if (ESTADOS_CONFIRMADOS.contains(estado) && getMontoCubierto().signum() == 0) {
            estado = EstadoReserva.CONFIRMADA;
            expiresAt = null;
        } else {
            recalcularEstado(senia, ahora);
        }
        return estado != anterior;
    }

    /** Un pago de esta reserva. */
    public java.util.Optional<Pago> pago(Long pagoId) {
        return pagos.stream().filter(p -> p.getId() != null && p.getId().equals(pagoId)).findFirst();
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
