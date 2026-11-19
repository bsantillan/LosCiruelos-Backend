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
 * Qué cubre una parte de un pago: la cancha o una línea de producto de la reserva. Es hija del pago
 * (se guarda y se borra por cascada). En un pago cobrado, la suma de sus imputaciones es lo que el
 * club se queda de él ({@link Pago#getMontoRetenido()}); en un QR esperando el pago son las que se
 * aplican cuando se cobra.
 */
@Entity
@Table(name = "pago_imputaciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PagoImputacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_id", nullable = false)
    private Pago pago;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConceptoImputacion concepto;

    // la línea de producto (null si es la cancha)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_producto_id")
    private ReservaProducto linea;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    /** Una imputación pedida por el personal (todavía sin validar contra la reserva). */
    public record Pedido(ConceptoImputacion concepto, Long lineaId, BigDecimal monto) {
    }

    public static PagoImputacion aCancha(BigDecimal monto) {
        return PagoImputacion.builder().concepto(ConceptoImputacion.CANCHA).monto(monto).build();
    }

    public static PagoImputacion aLinea(ReservaProducto linea, BigDecimal monto) {
        return PagoImputacion.builder().concepto(ConceptoImputacion.PRODUCTO).linea(linea).monto(monto).build();
    }

    public boolean esDeLaCancha() {
        return concepto == ConceptoImputacion.CANCHA;
    }

    public boolean esDe(ReservaProducto otra) {
        return linea != null && (linea == otra || (linea.getId() != null && linea.getId().equals(otra.getId())));
    }

    /** "Cancha" o el nombre del producto de la línea. */
    public String nombre() {
        return esDeLaCancha() ? "Cancha" : linea.getProducto().getNombre();
    }
}
