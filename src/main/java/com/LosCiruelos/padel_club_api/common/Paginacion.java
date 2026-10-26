package com.LosCiruelos.padel_club_api.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;

/** Página y tamaño de los listados, validados igual en toda la API. */
public final class Paginacion {

    private Paginacion() {
    }

    /** @throws ReservaException (400) si la página es negativa o el tamaño no está entre 1 y {@code maximo} */
    public static void validar(int pagina, int tamanio, int maximo) {
        if (pagina < 0) {
            throw new ReservaException("La página no puede ser negativa.");
        }
        if (tamanio < 1 || tamanio > maximo) {
            throw new ReservaException("El tamaño de página tiene que estar entre 1 y " + maximo + ".");
        }
    }

    /** Valida y arma la página. */
    public static PageRequest pagina(int pagina, int tamanio, int maximo, Sort orden) {
        validar(pagina, tamanio, maximo);
        return PageRequest.of(pagina, tamanio, orden);
    }
}
