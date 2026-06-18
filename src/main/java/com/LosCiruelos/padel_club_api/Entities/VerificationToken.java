package com.LosCiruelos.padel_club_api.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

import com.LosCiruelos.padel_club_api.Entities.Enum.TokenType;

@Entity
@Table(name = "VerificationTokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerificationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", referencedColumnName = "id")
    private Usuario usuario;

    @Column(nullable = false, unique = true, length = 6)
    private String token;

    @Column(nullable = false)
    private Instant fechaExpiracion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenType type;

    @Builder.Default
    @Column(nullable = false)
    private Boolean verificado = false;

    public boolean esExpirado() {
        return fechaExpiracion.isBefore(Instant.now());
    }

    public boolean esValido(String codigo) {
        return !esExpirado() && token.equals(codigo);
    }

    public static String generarCodigo() {
        int codigo = new java.util.Random().nextInt(900000) + 100000;
        return String.valueOf(codigo);
    }
}
