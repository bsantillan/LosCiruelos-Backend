package com.LosCiruelos.padel_club_api.reservas;

import com.LosCiruelos.padel_club_api.Entities.Usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Jugador de un partido (posiciones 1 a 4): un cliente registrado o un invitado sin cuenta,
 * del que solo se guarda el nombre y, opcionalmente, el teléfono.
 */
@Entity
@Table(name = "reserva_jugadores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservaJugador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "reserva_id", nullable = false)
    private Reserva reserva;

    @Column(nullable = false)
    private Integer posicion;

    // null: invitado
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    // Invitado: nombre y teléfono cargados por el ADMIN (null si es un cliente registrado)
    @Column(length = 100)
    private String nombre;

    @Column(length = 20)
    private String telefono;

    public boolean esInvitado() {
        return usuario == null;
    }

    /** Nombre para mostrar: el del cliente registrado o el del invitado. */
    public String nombreParaMostrar() {
        return Jugadores.nombreDe(usuario, nombre);
    }
}
