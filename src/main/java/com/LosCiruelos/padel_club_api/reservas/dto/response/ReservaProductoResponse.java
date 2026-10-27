package com.LosCiruelos.padel_club_api.reservas.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.Entities.Enum.TipoProducto;

import lombok.Data;

@Data
public class ReservaProductoResponse {
    private Long productoId;
    private String codigo;
    private String nombre;
    private TipoProducto tipo;
    private Integer cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal subtotal;
    // alquileres: unidades devueltas (null = todavía no se registró la devolución)
    private Integer cantidadDevuelta;
    private LocalDateTime devueltaAt;
}
