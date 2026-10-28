package com.LosCiruelos.padel_club_api.usuarios.entity;

import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Perfil del ADMIN: qué avisos de reservas recibe. */
@Entity
@Table(name = "admins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminProfile {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    // Interruptor general: en false no recibe ningún aviso (se conserva la elección de cada uno)
    @Builder.Default
    @Column(nullable = false)
    private Boolean activos = true;

    @Builder.Default
    @Column(name = "reserva_confirmada", nullable = false)
    private Boolean reservaConfirmada = true;

    @Builder.Default
    @Column(name = "saldo_cobrado", nullable = false)
    private Boolean saldoCobrado = true;

    @Builder.Default
    @Column(name = "reserva_cancelada", nullable = false)
    private Boolean reservaCancelada = true;

    @Builder.Default
    @Column(name = "reembolso_manual", nullable = false)
    private Boolean reembolsoManual = true;

    /** Perfil nuevo: recibe todos los avisos. */
    public static AdminProfile porDefecto(Usuario admin) {
        return AdminProfile.builder().usuario(admin).build();
    }

    /** Lo que eligió el ADMIN; {@code activos} null deja el interruptor general como estaba. */
    public void elegir(Boolean activos, Boolean reservaConfirmada, Boolean saldoCobrado, Boolean reservaCancelada,
            Boolean reembolsoManual) {
        if (activos != null) {
            this.activos = activos;
        }
        this.reservaConfirmada = reservaConfirmada;
        this.saldoCobrado = saldoCobrado;
        this.reservaCancelada = reservaCancelada;
        this.reembolsoManual = reembolsoManual;
    }

    /** Si le llega ese aviso: con los avisos activos y ese tipo elegido. */
    public boolean quiere(TipoAviso tipo) {
        if (!Boolean.TRUE.equals(activos)) {
            return false;
        }
        return switch (tipo) {
            case RESERVA_CONFIRMADA -> reservaConfirmada;
            case SALDO_COBRADO -> saldoCobrado;
            // el resultado de un reembolso pendiente es parte de la cancelación
            case RESERVA_CANCELADA, REEMBOLSO_REALIZADO -> reservaCancelada;
            case REEMBOLSO_MANUAL -> reembolsoManual;
            default -> false;
        };
    }
}
