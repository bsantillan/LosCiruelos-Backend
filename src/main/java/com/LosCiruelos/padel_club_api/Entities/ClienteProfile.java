package com.LosCiruelos.padel_club_api.Entities;

import java.time.LocalDateTime;

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

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Posicion posicion;

    @Column(name = "categoria_actualizada_at")
    private LocalDateTime categoriaActualizadaAt;

    // El cliente puede cambiar su categoría una sola vez, dentro de las 24 hs
    // desde que la cargó por primera vez.
    @Builder.Default
    @Column(name = "categoria_modificada", nullable = false)
    private Boolean categoriaModificada = false;
}
