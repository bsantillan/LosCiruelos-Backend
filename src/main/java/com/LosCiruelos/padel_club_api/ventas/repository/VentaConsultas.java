package com.LosCiruelos.padel_club_api.ventas.repository;

/** Consultas del listado de ventas que Spring Data no arma solo (los totales del filtro, con Criteria). */
public interface VentaConsultas {

    TotalesDelListado totalesDelListado(FiltroVentas filtro);

    /**
     * El primer y el último momento de registro de las ventas en [inicio, fin) (null: sin límite), con
     * MIN/MAX sobre created_at (con índice). Los dos null si no hay ventas.
     */
    java.time.LocalDateTime[] rangoDeRegistro(java.time.LocalDateTime inicio, java.time.LocalDateTime fin);
}
