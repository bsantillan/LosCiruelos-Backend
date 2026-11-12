package com.LosCiruelos.padel_club_api.reservas.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;

import lombok.Data;

@Data
public class ReservaProductoResponse {
    // id de la línea en la reserva (el que se usa en las imputaciones de un cobro)
    private Long lineaId;
    private Long productoId;
    private String codigo;
    private String nombre;
    private TipoProducto tipo;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
    // lo cobrado (y bonificado) que cubre esta línea y lo que le falta
    private BigDecimal montoPagado;
    private BigDecimal montoPendiente;
    // alquileres: unidades devueltas (null = todavía no se registró la devolución)
    private Integer cantidadDevuelta;
    private LocalDateTime devueltaAt;
}
