package com.LosCiruelos.padel_club_api.pagos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Jornada;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.ConceptoImputacion;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoDevolucion;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import java.util.HashSet;
import java.util.Iterator;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.CanalDePago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.OrdenQr;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
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

    // Qué cubre el pago (cancha y líneas de producto). En un QR esperando el pago, lo que se aplica al cobrarlo.
    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "pago", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PagoImputacion> imputaciones = new ArrayList<>();

    // Anulación (un cobro del club cargado por error): cuándo, quién y por qué
    @Column(name = "anulado_at")
    private LocalDateTime anuladoAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulado_por_id")
    private Usuario anuladoPor;

    @Column(name = "motivo_anulacion", length = 200)
    private String motivoAnulacion;

    // Lo que se le devolvió (o hay que devolverle) al cliente de este cobro, por línea
    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "pago", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Devolucion> devoluciones = new ArrayList<>();

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
        // un pago que ya no retiene plata (no se cobró, se venció o se devolvió entero) no cubre nada
        if (nuevo != EstadoPago.EN_PROCESO && getMontoRetenido().signum() == 0) {
            imputaciones.clear();
        }
    }

    /** Lo que cubre el pago: reemplaza las imputaciones que tuviera. */
    public void imputar(List<PagoImputacion> nuevas) {
        imputaciones.clear();
        nuevas.forEach(i -> {
            i.setPago(this);
            imputaciones.add(i);
        });
    }

    // ── Anular ───────────────────────────────────────────────────────────

    /**
     * Deja sin efecto un cobro cargado por error en el club (efectivo, transferencia, Cuenta DNI) o una
     * bonificación: el cobro no tendría que existir. Se anula entero, libera todo lo que cubría y deja
     * de contar en la caja. Los de Mercado Pago (online o con el QR) no se anulan: la plata entró, se
     * devuelven. Tampoco uno que ya tiene devoluciones.
     *
     * @throws EstadoInvalidoException (409) si no se puede anular
     */
    public void anular(Usuario quien, String motivo, LocalDateTime ahora) {
        if (provider == PaymentProvider.MERCADO_PAGO) {
            throw new EstadoInvalidoException(
                    "Un cobro de Mercado Pago (online o con el QR) no se anula: la plata entró, hay que devolverla.");
        }
        if (!devoluciones.isEmpty()) {
            throw new EstadoInvalidoException("El cobro tiene devoluciones: no se puede anular.");
        }
        if (estado != EstadoPago.APROBADO) {
            throw new EstadoInvalidoException("Solo se anula un cobro aprobado (este está " + estado + ").");
        }
        estado = EstadoPago.ANULADO;
        anuladoAt = ahora;
        anuladoPor = quien;
        motivoAnulacion = motivo;
        imputaciones.clear();
    }

    public boolean estaAnulado() {
        return estado == EstadoPago.ANULADO;
    }

    /** Lo registró el personal dentro de la jornada (lo que el EMPLEADO puede anular). */
    public boolean seRegistroEnLaJornada(Jornada jornada) {
        return registradoPorId != null && jornada.incluye(paidAt != null ? paidAt : createdAt);
    }

    // ── Devolver ─────────────────────────────────────────────────────────

    /** Lo que ya se devolvió o hay que devolver (null en un pago sin devoluciones es 0). */
    private BigDecimal reembolsado() {
        return montoReembolsado != null ? montoReembolsado : BigDecimal.ZERO;
    }

    /**
     * Devolución por línea: de cada línea pedida se devuelve ese monto, que no puede superar lo que
     * este cobro le imputó. Reduce esas imputaciones y acumula {@code montoReembolsado}. La devolución
     * queda A_REEMBOLSAR (a entregar a mano); si se devuelve por Mercado Pago, quien llama la marca
     * devuelta al confirmarse.
     *
     * @throws ReservaException (400) si una línea no está en el cobro, se repite o supera lo imputado
     */
    public Devolucion devolver(List<PagoImputacion.Pedido> pedidas, String motivo, Usuario quien, LocalDateTime ahora) {
        if (esBonificacion()) {
            throw new EstadoInvalidoException("Una bonificación no es plata: no se devuelve, se anula.");
        }
        if (!ESTADOS_COBRADOS.contains(estado) || imputaciones.isEmpty()) {
            throw new EstadoInvalidoException("El cobro #" + id + " no tiene nada para devolver.");
        }
        if (pedidas == null || pedidas.isEmpty()) {
            throw new ReservaException("Indicá al menos una línea a devolver.");
        }
        Set<String> vistas = new HashSet<>();
        List<PagoImputacion> afectadas = new ArrayList<>();
        for (PagoImputacion.Pedido pedida : pedidas) {
            if (pedida.monto() == null || pedida.monto().signum() <= 0) {
                throw new ReservaException("El monto de cada línea a devolver tiene que ser mayor a cero.");
            }
            boolean cancha = pedida.concepto() == ConceptoImputacion.CANCHA;
            if (!cancha && pedida.lineaId() == null) {
                throw new ReservaException("Indicá la línea (lineaId) de cada producto a devolver.");
            }
            if (!vistas.add(cancha ? "cancha" : "linea-" + pedida.lineaId())) {
                throw new ReservaException((cancha ? "La cancha" : "La línea #" + pedida.lineaId())
                        + " está repetida en la devolución.");
            }
            PagoImputacion imputacion = imputaciones.stream()
                    .filter(i -> cancha ? i.esDeLaCancha()
                            : !i.esDeLaCancha() && pedida.lineaId().equals(i.getLinea().getId()))
                    .findFirst()
                    .orElseThrow(() -> new ReservaException((cancha ? "La cancha" : "La línea #" + pedida.lineaId())
                            + " no está cubierta por el cobro #" + id + "."));
            if (pedida.monto().compareTo(imputacion.getMonto()) > 0) {
                throw new ReservaException("El cobro #%d le cubre %s a %s: no se pueden devolver %s.".formatted(id,
                        Dinero.texto(imputacion.getMonto()), imputacion.nombre(), Dinero.texto(pedida.monto())));
            }
            afectadas.add(imputacion);
        }
        Devolucion devolucion = nuevaDevolucion(motivo, quien, ahora);
        for (int i = 0; i < pedidas.size(); i++) {
            restar(devolucion, afectadas.get(i), pedidas.get(i).monto());
        }
        cerrarDevolucion(devolucion);
        return devolucion;
    }

    /**
     * Devolución de {@code aDevolver} al cancelar con reembolso: libera las imputaciones en proporción
     * a lo que cubre cada una (los centavos del redondeo van a la primera que tenga lugar).
     */
    public Devolucion devolverEnProporcion(BigDecimal aDevolver, String motivo, Usuario quien, LocalDateTime ahora) {
        Devolucion devolucion = nuevaDevolucion(motivo, quien, ahora);
        List<PagoImputacion> partes = new ArrayList<>(imputaciones);
        BigDecimal cubierto = Dinero.sumar(partes.stream().map(PagoImputacion::getMonto));
        BigDecimal aLiberar = aDevolver.min(cubierto);
        java.util.Map<PagoImputacion, BigDecimal> porParte = new java.util.LinkedHashMap<>();
        BigDecimal asignado = BigDecimal.ZERO;
        for (PagoImputacion parte : cubierto.signum() > 0 ? partes : List.<PagoImputacion>of()) {
            BigDecimal monto = aLiberar.multiply(parte.getMonto()).divide(cubierto, 2, java.math.RoundingMode.DOWN);
            porParte.put(parte, monto);
            asignado = asignado.add(monto);
        }
        BigDecimal centavos = aLiberar.subtract(asignado);
        for (PagoImputacion parte : partes) {
            BigDecimal lugar = parte.getMonto().subtract(porParte.get(parte)).min(centavos);
            porParte.merge(parte, lugar, BigDecimal::add);
            centavos = centavos.subtract(lugar);
        }
        porParte.forEach((parte, monto) -> {
            if (monto.signum() > 0) {
                restar(devolucion, parte, monto);
            }
        });
        devolucion.setMonto(aDevolver);
        montoReembolsado = reembolsado().add(aDevolver);
        devoluciones.add(devolucion);
        return devolucion;
    }

    /**
     * Devuelve todo lo que queda del cobro, con las líneas que cubría. La usan los cobros de Mercado
     * Pago que no se pueden aplicar (otro monto, reserva inactiva) y los devueltos desde el panel de MP.
     */
    public Devolucion devolverTodo(EstadoDevolucion estadoDevolucion, String motivo, LocalDateTime ahora) {
        BigDecimal queda = monto.subtract(reembolsado());
        Devolucion devolucion = nuevaDevolucion(motivo, null, ahora);
        new ArrayList<>(imputaciones).forEach(i -> restar(devolucion, i, i.getMonto()));
        devolucion.setMonto(queda);
        montoReembolsado = monto;
        if (estadoDevolucion == EstadoDevolucion.DEVUELTO) {
            devolucion.marcarDevuelta(null, ahora);
        } else {
            devolucion.setEstado(estadoDevolucion);
        }
        devoluciones.add(devolucion);
        return devolucion;
    }

    private Devolucion nuevaDevolucion(String motivo, Usuario quien, LocalDateTime ahora) {
        return Devolucion.builder().pago(this).monto(BigDecimal.ZERO).estado(EstadoDevolucion.A_REEMBOLSAR)
                .motivo(motivo != null && !motivo.isBlank() ? motivo.trim() : null).createdAt(ahora)
                .registradaPor(quien).build();
    }

    /** Pasa {@code monto} de la imputación a la devolución (y la borra si queda en 0). */
    private void restar(Devolucion devolucion, PagoImputacion imputacion, BigDecimal monto) {
        devolucion.getLineas().add(DevolucionLinea.builder().devolucion(devolucion).concepto(imputacion.getConcepto())
                .linea(imputacion.getLinea()).nombre(imputacion.nombre()).monto(monto).build());
        imputacion.setMonto(imputacion.getMonto().subtract(monto));
        if (imputacion.getMonto().signum() == 0) {
            imputaciones.remove(imputacion);
        }
    }

    /** El monto de la devolución es la suma de sus líneas; se acumula en lo reembolsado del pago. */
    private void cerrarDevolucion(Devolucion devolucion) {
        BigDecimal total = Dinero.sumar(devolucion.getLineas().stream().map(DevolucionLinea::getMonto));
        devolucion.setMonto(total);
        montoReembolsado = reembolsado().add(total);
        devoluciones.add(devolucion);
    }

    /**
     * Después de una devolución por línea o de entregar una: si el cobro todavía retiene plata y
     * estaba aprobado, sigue APROBADO; si no, toma el estado de sus devoluciones (alguna pendiente en
     * MP → REEMBOLSO_PENDIENTE; alguna por entregar → A_REEMBOLSAR; todas entregadas → DEVUELTO).
     */
    public void actualizarEstadoPorDevoluciones(LocalDateTime ahora) {
        if (estado == EstadoPago.APROBADO && monto.compareTo(reembolsado()) > 0) {
            return;
        }
        EstadoPago nuevo = devoluciones.stream().anyMatch(d -> d.getEstado() == EstadoDevolucion.REEMBOLSO_PENDIENTE)
                ? EstadoPago.REEMBOLSO_PENDIENTE
                : devoluciones.stream().anyMatch(Devolucion::estaPorEntregar) ? EstadoPago.A_REEMBOLSAR
                : EstadoPago.DEVUELTO;
        if (nuevo != estado) {
            cambiarEstado(nuevo, ahora);
        }
    }

    /** La devolución que Mercado Pago todavía no aceptó (la que reintenta el job), si hay. */
    public java.util.Optional<Devolucion> devolucionPendienteDeMp() {
        return devoluciones.stream().filter(d -> d.getEstado() == EstadoDevolucion.REEMBOLSO_PENDIENTE)
                .reduce((a, b) -> b);
    }

    /**
     * Mercado Pago resolvió lo que tenía pendiente: si lo devolvió, las devoluciones pendientes quedan
     * entregadas; si no, pasan a devolverse a mano.
     */
    public void resolverDevolucionesDeMp(boolean devueltas, LocalDateTime ahora) {
        for (Iterator<Devolucion> it = devoluciones.iterator(); it.hasNext();) {
            Devolucion d = it.next();
            if (d.getEstado() == EstadoDevolucion.REEMBOLSO_PENDIENTE
                    || (devueltas && d.getEstado() == EstadoDevolucion.A_REEMBOLSAR)) {
                if (devueltas) {
                    d.marcarDevuelta(null, ahora);
                } else {
                    d.setEstado(EstadoDevolucion.A_REEMBOLSAR);
                }
            }
        }
    }

    public java.util.Optional<Devolucion> devolucion(Long devolucionId) {
        return devoluciones.stream().filter(d -> d.getId() != null && d.getId().equals(devolucionId)).findFirst();
    }

    /** Las imputaciones que cuentan: las de un pago cobrado (las de un QR esperando el pago, todavía no). */
    public List<PagoImputacion> imputacionesVigentes() {
        return ESTADOS_COBRADOS.contains(estado) ? imputaciones : List.of();
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

    /** Como {@link #ordenQr()}, con otra clave para una devolución en partes. */
    public OrdenQr ordenQr(String operacion) {
        return new OrdenQr(orderId, "reserva-" + reserva.getId() + "-pago-" + id,
                claveIdempotencia + "-" + operacion, monto);
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