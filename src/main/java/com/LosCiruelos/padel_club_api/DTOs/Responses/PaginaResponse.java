package com.LosCiruelos.padel_club_api.DTOs.Responses;

import java.util.List;

import org.springframework.data.domain.Page;

/** Página de resultados con un formato JSON propio y estable (no se serializa el Page de Spring). */
public record PaginaResponse<T>(List<T> contenido, int pagina, int tamanio, long totalElementos, int totalPaginas) {

    public static <T> PaginaResponse<T> de(Page<T> page) {
        return new PaginaResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
