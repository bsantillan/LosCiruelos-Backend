package com.LosCiruelos.padel_club_api.club.entity;

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

/** Una foto de una cancha: el archivo en el disco (nombre UUID) y su orden (1 a 6). Hija de la cancha. */
@Entity
@Table(name = "cancha_fotos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CanchaFoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cancha_id", nullable = false)
    private Cancha cancha;

    @Column(nullable = false, length = 80, unique = true)
    private String archivo;

    @Column(nullable = false)
    private Integer orden;
}
