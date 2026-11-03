package com.LosCiruelos.padel_club_api.reservas.dto.response;

import java.math.BigDecimal;

import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductoDisponibleResponse {
    private Long id;
    private String codigo;
    private String nombre;
    private TipoProducto tipo;
    private BigDecimal precio;
    // unidades que se pueden pedir para ese horario (null: sin límite, el producto no controla stock)
    private Integer disponibles;
    // si el cliente lo ve al reservar (el panel muestra primero esos y el resto como "Más productos")
    private Boolean ofrecerAlReservar;
}
