package com.LosCiruelos.padel_club_api.club.entity;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Producto del stock del club (sección de configuración).
 * VENTA: {@code stock} son las unidades disponibles hoy y baja con cada reserva.
 * ALQUILER: {@code stock} es el total de unidades; se controla cuántas están en uso en cada horario.
 * Sin {@code controlaStock} no se limitan las unidades ni se mueve el stock. Solo los que se
 * ofrecen al reservar ({@code ofrecerAlReservar}) los ve y los pide el cliente; el resto los
 * agrega el personal.
 */
@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    // Productos del sistema que se piden en la reserva con campos propios
    public static final String CODIGO_PELOTAS = "PELOTAS";
    public static final String CODIGO_PALETAS = "PALETAS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Solo los productos del sistema tienen código; los que crea el admin no
    @Column(length = 30, unique = true)
    private String codigo;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 300)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoProducto tipo;

    // precio por unidad
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;

    @Column(nullable = false)
    private Integer stock;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;

    // el cliente lo ve y lo pide al reservar; si no, solo lo agrega el personal
    @Builder.Default
    @Column(name = "ofrecer_al_reservar", nullable = false)
    private Boolean ofrecerAlReservar = false;

    // false: no se limitan las unidades ni se mueve el stock
    @Builder.Default
    @Column(name = "controla_stock", nullable = false)
    private Boolean controlaStock = true;

    public boolean esDelSistema() {
        return codigo != null;
    }

    /** Lleva la cuenta de unidades: si no, se puede pedir sin límite y el stock no se mueve. */
    public boolean llevaStock() {
        return Boolean.TRUE.equals(controlaStock);
    }

    /** Lo puede pedir el cliente al reservar (activo y ofrecido). */
    public boolean seOfreceAlReservar() {
        return Boolean.TRUE.equals(activo) && Boolean.TRUE.equals(ofrecerAlReservar);
    }
}
