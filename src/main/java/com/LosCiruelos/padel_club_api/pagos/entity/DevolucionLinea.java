package com.LosCiruelos.padel_club_api.pagos.entity;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.pagos.entity.enums.ConceptoImputacion;
import com.LosCiruelos.padel_club_api.reservas.entity.ReservaProducto;

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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Qué parte de una devolución corresponde a la cancha o a una línea de producto. Guarda el nombre:
 * la línea se puede sacar de la reserva después de devolverla (queda en null).
 */
@Entity
@Table(name = "pago_devolucion_lineas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DevolucionLinea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "devolucion_id", nullable = false)
    private Devolucion devolucion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConceptoImputacion concepto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_producto_id")
    private ReservaProducto linea;

    // "Cancha" o el nombre del producto al momento de devolverlo
    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;
}
