package com.LosCiruelos.padel_club_api.DTOs.Requests;

import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class NotificacionesRequest {

    // EMAIL o WHATSAPP. Elegir WHATSAPP es aceptar recibir mensajes por WhatsApp
    @NotNull(message = "El canal es obligatorio")
    private CanalNotificacion canal;

    // Opcional: cargar o cambiar el teléfono (si cambia, hay que volver a verificarlo)
    @Pattern(regexp = "^[0-9+() -]{8,25}$", message = "Teléfono inválido")
    private String telefono;
}
