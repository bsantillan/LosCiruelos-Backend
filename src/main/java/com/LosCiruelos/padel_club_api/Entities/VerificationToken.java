package com.LosCiruelos.padel_club_api.Entities;

import jakarta.persistence.*;
import lombok.*;

import java.security.SecureRandom;
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

    public static final int MAX_INTENTOS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", referencedColumnName = "id")
    private Usuario usuario;

    @Column(nullable = false, length = 6)
    private String token;

    @Column(nullable = false)
    private Instant fechaExpiracion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TokenType type;

    @Builder.Default
    @Column(nullable = false)
    private Boolean verificado = false;

    // Intentos fallidos: al llegar a MAX_INTENTOS el código queda inutilizable
    @Builder.Default
    @Column(nullable = false)
    private Integer intentos = 0;

    public boolean esExpirado() {
        return fechaExpiracion.isBefore(Instant.now());
    }

    public boolean estaBloqueado() {
        return intentos >= MAX_INTENTOS;
    }

    public boolean esValido(String codigo) {
        return !esExpirado() && !estaBloqueado() && token.equals(codigo);
    }

    public void registrarIntentoFallido() {
        intentos++;
    }

    public static String generarCodigo() {
        return String.valueOf(RANDOM.nextInt(900000) + 100000);
    }
}
