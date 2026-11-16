package com.LosCiruelos.padel_club_api.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Página de resultados con un formato JSON propio y estable (no se serializa el Page de Spring).
 *
 * @param rango la primera y la última fecha de toda la lista, sin el filtro de fechas (null en las
 *              listas sin fecha)
 */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas,
        RangoDeFechas rango) {

    public PaginaResponse(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {
        this(contenido, pagina, tamanio, totalElementos, totalPaginas, null);
    }

    public static <T> PaginaResponse<T> de(Page<T> page) {
        return de(page, null);
    }

    public static <T> PaginaResponse<T> de(Page<T> page, RangoDeFechas rango) {
        return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), rango);
    }
}
