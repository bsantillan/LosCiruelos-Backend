package com.LosCiruelos.padel_club_api.Entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

// Se determina el nombre de la clase en la BD
@Entity(name = "Telefono")
@Data
@NoArgsConstructor
public class Telefono {
    
    // Se define el ID como autoincremental
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, name = "codigo")
    private Integer codigo;

    @Column(nullable = false, name = "numero")
    private Integer numero;

}
