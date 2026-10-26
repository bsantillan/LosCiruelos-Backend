package com.LosCiruelos.padel_club_api.Entities;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import com.LosCiruelos.padel_club_api.common.Turno;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Turno fijo: el mismo partido (cancha, día de la semana, horario y jugadores) todas las semanas
 * desde {@code desde}, hasta que el ADMIN lo da de baja. Bloquea el horario siempre; los
 * partidos de cada semana los crea un job con algunas semanas de anticipación.
 */
@Entity
@Table(name = "turnos_fijos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TurnoFijo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cancha_id", nullable = false)
    private Cancha cancha;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 10)
    private DayOfWeek diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fin", nullable = false)
    private LocalTime horaFin;

    // primera fecha en la que se juega
    @Column(nullable = false)
    private LocalDate desde;

    @Column(name = "cantidad_pelotas", nullable = false)
    private Integer cantidadPelotas;

    @Column(name = "cantidad_paletas", nullable = false)
    private Integer cantidadPaletas;

    @Column(nullable = false)
    private Boolean activo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private Usuario createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // cuándo se dio de baja (null si sigue activo)
    @Column(name = "dado_de_baja_at")
    private LocalDateTime dadoDeBajaAt;

    @Builder.Default
    @OrderBy("posicion")
    @OneToMany(mappedBy = "turnoFijo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TurnoFijoJugador> jugadores = new ArrayList<>();

    /** El turno de esa fecha (cancha aparte). */
    public Turno turnoEl(LocalDate fecha) {
        return new Turno(fecha, horaInicio, horaFin);
    }

    /** La fecha cae en este turno fijo: mismo día de la semana y desde la fecha de inicio. */
    public boolean seJuegaEl(LocalDate fecha) {
        return fecha.getDayOfWeek() == diaSemana && !fecha.isBefore(desde);
    }
}
