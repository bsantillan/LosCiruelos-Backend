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

/** Jugador de un turno fijo: se copia a cada partido que se crea. Igual que {@link ReservaJugador}. */
@Entity
@Table(name = "turno_fijo_jugadores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnoFijoJugador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "turno_fijo_id", nullable = false)
    private TurnoFijo turnoFijo;

    @Column(nullable = false)
    private Integer posicion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(length = 100)
    private String nombre;

    @Column(length = 20)
    private String telefono;

    public String nombreParaMostrar() {
        return Jugadores.nombreDe(usuario, nombre);
    }
}
