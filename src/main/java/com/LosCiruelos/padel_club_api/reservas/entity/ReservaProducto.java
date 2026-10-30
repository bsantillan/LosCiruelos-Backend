package com.LosCiruelos.padel_club_api.reservas.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.club.entity.Producto;

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
import lombok.Setter;

/** Producto pedido en una reserva, con el precio unitario vigente al momento de reservar. */
@Entity
@Table(name = "reserva_productos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reserva reserva;

    @ManyToOne(optional = false)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false)
    private Integer cantidad;

    // se congela al reservar: si después cambia el precio, la reserva no cambia
    @Column(name = "precio_unitario", nullable = false, precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    // Alquileres: unidades que devolvieron al terminar el turno (null = todavía no se registró).
    // Mientras sea null y el turno haya terminado, esas unidades cuentan como afuera del club.
    @Column(name = "cantidad_devuelta")
    private Integer cantidadDevuelta;

    @Column(name = "devuelta_at")
    private LocalDateTime devueltaAt;

    public boolean esAlquiler() {
        return producto.getTipo() == TipoProducto.ALQUILER;
    }

    public boolean devolucionRegistrada() {
        return cantidadDevuelta != null;
    }

    public BigDecimal getSubtotal() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }
}
