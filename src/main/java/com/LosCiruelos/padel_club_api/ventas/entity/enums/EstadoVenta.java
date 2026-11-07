package com.LosCiruelos.padel_club_api.ventas.entity.enums;

/** En qué quedó una venta suelta. Solo las COBRADA cuentan en la caja y en lo vendido. */
public enum EstadoVenta {
    COBRADA,
    // sin efecto: el stock volvió y no cuenta en la caja (queda quién, cuándo y por qué)
    ANULADA
}
