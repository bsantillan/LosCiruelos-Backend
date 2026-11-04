package com.LosCiruelos.padel_club_api.ventas.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

/**
 * Devolución de una venta suelta (todo o parte): la registra el ADMIN y cuenta en la caja del día en
 * que se hace. Es parte de la venta (se guarda por cascada); se crea con {@link Venta#devolver}.
 */
@Entity
@Table(name = "venta_devoluciones")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaDevolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "registrada_por_id", nullable = false)
    private Usuario registradaPor;

    @Column(nullable = false, length = 200)
    private String motivo;

    // lo que se le devuelve al cliente: las unidades al precio de la venta
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Builder.Default
    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<VentaDevolucionItem> items = new ArrayList<>();

    /** Unidades de cada producto que vuelven al stock (las de los ítems que sirven). */
    public Map<Producto, Integer> aReponer() {
        Map<Producto, Integer> cantidades = new LinkedHashMap<>();
        items.stream()
                .filter(i -> Boolean.TRUE.equals(i.getVuelveAlStock()))
                .forEach(i -> cantidades.merge(i.getVentaItem().getProducto(), i.getCantidad(), Integer::sum));
        return cantidades;
    }

    /** Lo que se pide devolver de un producto de la venta. */
    public record Pedido(Long productoId, int cantidad, boolean vuelveAlStock) {
    }
}
