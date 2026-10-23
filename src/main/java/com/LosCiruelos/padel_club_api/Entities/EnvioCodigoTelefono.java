package com.LosCiruelos.padel_club_api.Entities;

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
import lombok.Setter;

/**
 * Registro de cada código de verificación mandado por WhatsApp. Sirve para los topes por usuario
 * y por número (cada mensaje cuesta y no se puede usar para molestar a un número ajeno).
 */
@Entity
@Table(name = "envios_codigo_telefono")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnvioCodigoTelefono {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(nullable = false, length = 20)
    private String telefono;

    @Column(name = "enviado_at", nullable = false)
    private LocalDateTime enviadoAt;
}
