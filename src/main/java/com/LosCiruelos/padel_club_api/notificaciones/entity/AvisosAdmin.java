package com.LosCiruelos.padel_club_api.notificaciones.entity;

import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Qué avisos de reservas recibe cada ADMIN. Si un ADMIN no tiene fila, recibe todos. */
@Entity
@Table(name = "avisos_admin")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvisosAdmin {

    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

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

    public static AvisosAdmin porDefecto(Long usuarioId) {
        return AvisosAdmin.builder().usuarioId(usuarioId).build();
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
