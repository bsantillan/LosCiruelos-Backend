package com.LosCiruelos.padel_club_api.Entities;

import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Configuracion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Configuracion {

    @Id
    private Long id;

    @Column(nullable = false, name = "monto_reserva")
    private Float montoReserva;

    @Column(nullable = false, name = "porcentaje_senia")
    private Float porcentajeSeña;

    @Column(nullable = false, name = "duracion_minima_turno")
    private Integer duracionMinimaTurno;

    @Column(nullable = false, name = "duracion_maxima_turno")
    private Integer duracionMaximaTurno;

    @Column(nullable = false, name = "monto_x_media_hora")
    private Float montoXMediHora;

    @Builder.Default
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(nullable = false, name = "configuracion_general_id")
    private List<DiaApertura> dias_apertura = new ArrayList<>();
}