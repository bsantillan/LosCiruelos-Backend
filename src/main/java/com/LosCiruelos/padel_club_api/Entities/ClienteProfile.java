package com.LosCiruelos.padel_club_api.Entities;

import com.LosCiruelos.padel_club_api.Entities.Enum.Categoria;
import com.LosCiruelos.padel_club_api.Entities.Enum.Posicion;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "Clientes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClienteProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    private Usuario usuario;

    @Column(length = 20)
    private Categoria categoria;

    @Column(length = 10)
    private Posicion posicion;
}
