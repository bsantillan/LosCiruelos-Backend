package com.LosCiruelos.padel_club_api.usuarios.dto.response;

import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Posicion;

import lombok.Data;

@Data
public class PerfilResponse {
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    // el teléfono se confirmó con el código de WhatsApp
    private Boolean telefonoVerificado;
    // canal elegido para los avisos (EMAIL o WHATSAPP)
    private CanalNotificacion canalNotificaciones;
    // CLIENTE y ADMIN: datos cargados y celular verificado (el CLIENTE además categoría y posición)
    private Boolean perfilCompleto;
    private Categoria categoria;
    private Posicion posicion;
    private Integer cantDiasMiembro;
    // partidos jugados (reservas COMPLETADA)
    private Integer cantPartidos;
    // null si todavía no jugó ningún partido
    private Integer diasDesdeUltimoPartido;
    private Long cantPartidosEsteMes;
    private LocalDateTime categoriaActualizadaAt;
    // true si el cliente todavía puede cambiar su categoría por su cuenta
    private Boolean categoriaEditable;
}
