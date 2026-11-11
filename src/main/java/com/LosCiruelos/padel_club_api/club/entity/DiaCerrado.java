package com.LosCiruelos.padel_club_api.club.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Un día puntual en que el club no abre (es parte de la configuración: se crea con {@link Configuracion#cerrar}). */
@Entity
@Table(name = "dias_cerrados")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DiaCerrado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    @Column(nullable = false, length = 200)
    private String motivo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // el ADMIN que lo cerró
    @Column(name = "created_by_id", nullable = false)
    private Long createdById;
}
