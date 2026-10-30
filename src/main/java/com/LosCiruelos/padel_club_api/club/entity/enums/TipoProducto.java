package com.LosCiruelos.padel_club_api.club.entity.enums;

public enum TipoProducto {
    VENTA,      // se entrega y no vuelve: el stock baja al reservar (ej. pelotas)
    ALQUILER    // se presta durante el turno: el stock es el total de unidades (ej. paletas)
}
