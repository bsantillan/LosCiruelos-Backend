package com.LosCiruelos.padel_club_api.DTOs.Responses;

import com.LosCiruelos.padel_club_api.Entities.Enum.Categoria;
import com.LosCiruelos.padel_club_api.Entities.Enum.Posicion;

/** Cliente encontrado por el personal (para filtrar reservas o reservar a su nombre). */
public record ClienteBusquedaResponse(Long id, String nombre, String apellido, String email, String telefono,
        boolean telefonoVerificado, Categoria categoria, Posicion posicion, boolean perfilCompleto,
        boolean enabled) {
}
