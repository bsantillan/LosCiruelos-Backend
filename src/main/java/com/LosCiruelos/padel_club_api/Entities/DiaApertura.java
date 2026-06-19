package com.LosCiruelos.padel_club_api.Entities;

import java.time.LocalTime;
import java.util.Set;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Dias")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiaApertura {

    // Valores permitidos para la variable dia
    private static final Set<String> dias_validos = Set.of(
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo");

    // Se define el ID como autoincremental
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "dia", length = 10)
    private String dia;

    @Column(nullable = false, name = "horario_inicio")
    private LocalTime horario_inicio;

    @Column(nullable = false, name = "horario_fin")
    private LocalTime horario_fin;

    // Valida que el valor a ingresar o modificar en el dia sea de Lunes a Domingos
    @Transient
    public void set_dia(String dia) {
        if (!dias_validos.contains(dia)) {
            throw new IllegalArgumentException("Día inválido: " + dia);
        }
        this.dia = dia;
    }
}
