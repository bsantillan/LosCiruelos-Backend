package com.LosCiruelos.padel_club_api.usuarios.entity;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.usuarios.entity.enums.AuthProvider;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================
    // DATOS PERSONALES
    // =========================

    @Column(nullable = true, length = 50)
    private String nombre;

    @Column(nullable = true, length = 50)
    private String apellido;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // Formato de WhatsApp: código de país y número, solo dígitos (ej. 5491123456789)
    @Column(length = 20)
    private String telefono;

    // El usuario ingresó el código que le llegó por WhatsApp a ese teléfono
    @Builder.Default
    @Column(name = "telefono_verificado", nullable = false)
    private Boolean telefonoVerificado = false;

    // =========================
    // NOTIFICACIONES
    // =========================

    // Canal elegido para los avisos. WHATSAPP se usa solo con el teléfono verificado; si no, va por mail
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "canal_notificaciones", nullable = false, length = 20)
    private CanalNotificacion canalNotificaciones = CanalNotificacion.EMAIL;

    // Cuándo aceptó recibir mensajes por WhatsApp (consentimiento que pide Meta)
    @Column(name = "acepta_whatsapp_at")
    private LocalDateTime aceptaWhatsappAt;

    // Cuándo aceptó recibir notificaciones del club (checkbox del registro)
    @Column(name = "notificaciones_aceptadas_at")
    private LocalDateTime notificacionesAceptadasAt;

    // Avisos opcionales del cliente. Las cancelaciones se avisan siempre.
    @Builder.Default
    @Column(name = "recibe_recordatorios", nullable = false)
    private Boolean recibeRecordatorios = true;

    @Builder.Default
    @Column(name = "recibe_promociones", nullable = false)
    private Boolean recibePromociones = false;

    // =========================
    // AUTH
    // =========================

    // Puede ser null hasta que complete activación
    @Column(name = "password_hash")
    private String passwordHash;

    @Builder.Default
    @Column(nullable = false)
    private Boolean termsAccepted = false;

    @Column(name = "terms_accepted_at", nullable = false)
    private LocalDateTime termsAcceptedAt;

    @Builder.Default
    @Column(name = "email_verificado", nullable = false)
    private Boolean emailVerificado = false;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role rol;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider = AuthProvider.LOCAL;

    // =========================
    // ClienteProfile
    // =========================

    @OneToOne(mappedBy = "usuario", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private ClienteProfile clienteProfile;

    /** Nombre, apellido y celular verificado con el código de WhatsApp (igual que el mail). */
    public boolean datosPersonalesCompletos() {
        return telefono != null && Boolean.TRUE.equals(telefonoVerificado) && nombre != null && apellido != null;
    }

    /**
     * Elige el canal de los avisos. WhatsApp necesita el teléfono cargado y registra el
     * consentimiento la primera vez; volver al mail lo retira.
     */
    public void elegirCanal(CanalNotificacion canal, LocalDateTime ahora) {
        if (canal == CanalNotificacion.WHATSAPP) {
            if (telefono == null) {
                throw new ReservaException("Cargá tu teléfono para recibir avisos por WhatsApp.");
            }
            if (aceptaWhatsappAt == null) {
                aceptaWhatsappAt = ahora;
            }
        } else {
            aceptaWhatsappAt = null;
        }
        canalNotificaciones = canal;
    }

    /** Avisos opcionales del cliente (las cancelaciones se avisan siempre). */
    public void elegirSuscripciones(Boolean recordatorios, Boolean promociones) {
        recibeRecordatorios = recordatorios;
        recibePromociones = promociones;
    }
}
