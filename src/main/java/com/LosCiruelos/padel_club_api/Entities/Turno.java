package Grupo11.Seminario.Entities;

import java.time.LocalDate;
import java.time.LocalTime;

import Grupo11.Seminario.Entities.Enum.EstadoTurno;
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
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class Turno {
    
    // Se define el ID como autoincremental
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, name = "fecha")
    private LocalDate fecha;

    @Column(nullable = true, name = "horario_bloqueo")
    private LocalTime horarioBloqueo;

    @Column(nullable = false, name = "horario_inicio")
    private LocalTime horarioInicio;

    @Column(nullable = false, name = "horario_fin")
    private LocalTime horario_fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "estado")
    private EstadoTurno estado;

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.MERGE}) // Cambia CascadeType.ALL a CascadeType.PERSIST y CascadeType.MERGE
    @JoinColumn(nullable = true, name = "cancha_id")
    private Cancha cancha;
}
