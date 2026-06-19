package com.LosCiruelos.padel_club_api.Entities;

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
import lombok.Setter;

@Entity
@Table(name = "Canchas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cancha {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, name = "numero")
    private Integer numero;

    @Column(nullable = false, name = "descripcion", length = 300)
    private String descripccion;
}
