package com.LosCiruelos.padel_club_api.usuarios.entity;

import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
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
    // PERFIL DE CADA ROL
    // =========================

    @OneToOne(mappedBy = "usuario", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private ClienteProfile clienteProfile;

    @OneToOne(mappedBy = "usuario", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private AdminProfile adminProfile;

    @OneToOne(mappedBy = "usuario", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private EmpleadoProfile empleadoProfile;

    @OneToOne(mappedBy = "usuario", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private ProfesorProfile profesorProfile;

    /**
     * Cambia el rol y deja solo el perfil de ese rol: le crea el suyo si no lo tiene (vacío;
     * los datos del jugador se cargan aparte) y saca los de los otros roles. Los perfiles se
     * guardan y se borran por cascada con la cuenta.
     */
    public void asignarRol(Role nuevo) {
        rol = nuevo;
        clienteProfile = nuevo != Role.CLIENTE ? null
                : clienteProfile != null ? clienteProfile : ClienteProfile.builder().usuario(this).build();
        adminProfile = nuevo != Role.ADMIN ? null
                : adminProfile != null ? adminProfile : AdminProfile.porDefecto(this);
        empleadoProfile = nuevo != Role.EMPLEADO ? null
                : empleadoProfile != null ? empleadoProfile : EmpleadoProfile.builder().usuario(this).build();
        profesorProfile = nuevo != Role.PROFESOR ? null
                : profesorProfile != null ? profesorProfile : ProfesorProfile.builder().usuario(this).build();
    }

    /** Los avisos que elige el ADMIN; sin perfil todavía, recibe todos. */
    public AdminProfile avisosAdmin() {
        return adminProfile != null ? adminProfile : AdminProfile.porDefecto(this);
    }

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

    /**
     * Si le llega el recordatorio del turno: solo al cliente que lo eligió en su perfil. El resto
     * de los roles (por ejemplo, un EMPLEADO que juega un partido) no lo recibe.
     */
    public boolean recibeRecordatorios() {
        return clienteProfile != null && Boolean.TRUE.equals(clienteProfile.getRecibeRecordatorios());
    }

    /** Si recibe novedades y promociones: solo el cliente que las eligió. */
    public boolean recibePromociones() {
        return clienteProfile != null && Boolean.TRUE.equals(clienteProfile.getRecibePromociones());
    }
}
