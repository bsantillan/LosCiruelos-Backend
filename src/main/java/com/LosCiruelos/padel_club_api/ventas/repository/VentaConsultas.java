package com.LosCiruelos.padel_club_api.ventas.repository;

/** Consultas del listado de ventas que Spring Data no arma solo (los totales del filtro, con Criteria). */
public interface VentaConsultas {

    TotalesDelListado totalesDelListado(FiltroVentas filtro);
}
