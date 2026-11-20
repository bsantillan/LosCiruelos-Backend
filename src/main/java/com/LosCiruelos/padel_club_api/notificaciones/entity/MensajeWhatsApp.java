package com.LosCiruelos.padel_club_api.notificaciones.entity;

import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;
import com.LosCiruelos.padel_club_api.notificaciones.entity.enums.MotivoWhatsApp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Un WhatsApp que el club mandó bien (cada uno lo cobra Meta). Solo para contarlos: no guarda a quién
 * ni el teléfono.
 */
@Entity
@Table(name = "mensajes_whatsapp")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MensajeWhatsApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MotivoWhatsApp motivo;

    // qué aviso (solo si el motivo es AVISO)
    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private TipoAviso aviso;

    @Column(name = "para_cliente", nullable = false)
    private Boolean paraCliente;

    @Column(name = "enviado_at", nullable = false)
    private LocalDateTime enviadoAt;

    public static MensajeWhatsApp codigo(boolean paraCliente, LocalDateTime ahora) {
        return new MensajeWhatsApp(null, MotivoWhatsApp.CODIGO, null, paraCliente, ahora);
    }

    public static MensajeWhatsApp aviso(TipoAviso aviso, boolean paraCliente, LocalDateTime ahora) {
        return new MensajeWhatsApp(null, MotivoWhatsApp.AVISO, aviso, paraCliente, ahora);
    }
}
