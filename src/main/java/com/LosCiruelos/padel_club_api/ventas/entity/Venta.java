package com.LosCiruelos.padel_club_api.ventas.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Venta suelta en el club: productos sin reserva, partido ni turno. Se cobra en el momento y entra
 * en la caja del día; el cliente es opcional. Una venta registrada no se edita ni se borra: se
 * anula (queda quién, cuándo y por qué); solo el cliente asociado se puede cambiar.
 */
@Entity
@Table(name = "ventas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MedioDePago medio;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoVenta estado = EstadoVenta.COBRADA;

    // Venta anulada: cuándo, quién y por qué (null mientras está vigente)
    @Column(name = "anulada_at")
    private LocalDateTime anuladaAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulada_por_id")
    private Usuario anuladaPor;

    @Column(name = "motivo_anulacion", length = 200)
    private String motivoAnulacion;

    // opcional
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Usuario cliente;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "registrado_por_id", nullable = false)
    private Usuario registradoPor;

    @Builder.Default
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VentaItem> items = new ArrayList<>();

    // Venta con Cuenta DNI: el ADMIN comprobó en la cuenta del club que la plata entró (null: sin verificar)
    @Column(name = "verificado_at")
    private LocalDateTime verificadoAt;

    @Column(name = "verificado_por_id")
    private Long verificadoPorId;

    /** Marca (o desmarca) una venta con Cuenta DNI como verificada. Los otros medios no se verifican. */
    public void verificar(boolean verificado, Long adminId, LocalDateTime ahora) {
        if (medio != MedioDePago.CUENTA_DNI) {
            throw new EstadoInvalidoException("Solo se verifican las ventas con Cuenta DNI.");
        }
        verificadoAt = verificado ? ahora : null;
        verificadoPorId = verificado ? adminId : null;
    }

    public boolean estaVerificada() {
        return verificadoAt != null;
    }

    public boolean estaAnulada() {
        return estado == EstadoVenta.ANULADA;
    }

    /**
     * Si {@code quien} puede anularla: el ADMIN, cualquier venta; el EMPLEADO, solo las de hoy y que
     * el ADMIN todavía no verificó contra la cuenta.
     *
     * @throws PermisoDenegadoException (403) si no puede
     */
    public void validarQuePuedeAnularla(Usuario quien, LocalDate hoy) {
        if (Roles.esAdmin(quien)) {
            return;
        }
        if (!esDelDia(hoy)) {
            throw new PermisoDenegadoException("Solo podés anular ventas de hoy.");
        }
        if (estaVerificada()) {
            throw new PermisoDenegadoException("La venta ya está verificada: solo el ADMIN la puede anular.");
        }
    }

    /**
     * La deja sin efecto: deja de contar en la caja y en lo vendido. El stock lo repone quien la anula
     * (con {@link #cantidades()}).
     */
    public void anular(Usuario quien, String motivo, LocalDateTime ahora) {
        if (estaAnulada()) {
            throw new EstadoInvalidoException("La venta ya está anulada.");
        }
        estado = EstadoVenta.ANULADA;
        anuladaAt = ahora;
        anuladaPor = quien;
        motivoAnulacion = motivo;
    }

    /** Asocia la venta a otro cliente (null: venta al público). Es lo único que se edita: no mueve plata ni stock. */
    public void cambiarCliente(Usuario nuevo) {
        if (estaAnulada()) {
            throw new EstadoInvalidoException("La venta está anulada: no se puede modificar.");
        }
        cliente = nuevo;
    }

    public boolean esDelDia(LocalDate dia) {
        return createdAt.toLocalDate().equals(dia);
    }

    /** Unidades de cada producto de la venta. */
    public Map<Producto, Integer> cantidades() {
        Map<Producto, Integer> cantidades = new LinkedHashMap<>();
        items.forEach(i -> cantidades.merge(i.getProducto(), i.getCantidad(), Integer::sum));
        return cantidades;
    }

    /** Suma unidades de un producto, al precio de hoy (si ya estaba, se suman a ese ítem). */
    public void agregar(Producto producto, int cantidad) {
        items.stream()
                .filter(i -> i.getProducto().getId().equals(producto.getId()))
                .findFirst()
                .ifPresentOrElse(i -> i.setCantidad(i.getCantidad() + cantidad),
                        () -> items.add(VentaItem.builder().venta(this).producto(producto).cantidad(cantidad)
                                .precioUnitario(producto.getPrecio()).build()));
        total = Dinero.sumar(items.stream().map(VentaItem::getSubtotal));
    }
}
