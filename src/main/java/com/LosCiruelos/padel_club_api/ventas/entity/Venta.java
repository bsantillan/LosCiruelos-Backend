package com.LosCiruelos.padel_club_api.ventas.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
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
 * en la caja del día; el cliente es opcional.
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
