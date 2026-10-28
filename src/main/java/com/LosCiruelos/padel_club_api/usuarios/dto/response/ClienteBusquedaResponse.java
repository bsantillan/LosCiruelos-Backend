package com.LosCiruelos.padel_club_api.usuarios.dto.response;

import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Posicion;

/** Cliente encontrado por el personal (para filtrar reservas o reservar a su nombre). */
public record ClienteBusquedaResponse(Long id, String nombre, String apellido, String email, String telefono,
        boolean telefonoVerificado, Categoria categoria, Posicion posicion, boolean perfilCompleto,
        boolean enabled) {
}
