package com.LosCiruelos.padel_club_api.ventas.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Unidades de un ítem de la venta que se devolvieron, y si volvieron al stock (si el producto sirve). */
@Entity
@Table(name = "venta_devolucion_items")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VentaDevolucionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "devolucion_id", nullable = false)
    private VentaDevolucion devolucion;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_item_id", nullable = false)
    private VentaItem ventaItem;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "vuelve_al_stock", nullable = false)
    private Boolean vuelveAlStock;

    public BigDecimal getSubtotal() {
        return ventaItem.getPrecioUnitario().multiply(BigDecimal.valueOf(cantidad));
    }
}
