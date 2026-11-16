package com.LosCiruelos.padel_club_api.club.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.club.entity.enums.EstadoNovedad;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;

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

/**
 * Una novedad del club (un torneo, clases, un aviso) que publica el ADMIN para la página de inicio.
 * El texto "Para más información, comunicate con el club" lo agrega el front: no se guarda.
 */
@Entity
@Table(name = "novedades")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Novedad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String titulo;

    @Column(nullable = false, length = 300)
    private String descripcion;

    // opcional, ej. "Torneo", "Clases", "Club"
    @Column(length = 30)
    private String etiqueta;

    // el archivo de la imagen en ARCHIVOS_DIR (opcional)
    @Column(length = 80)
    private String imagen;

    @Builder.Default
    @Column(nullable = false)
    private Boolean publicada = false;

    @Column(name = "publicada_at")
    private LocalDateTime publicadaAt;

    // después de esta fecha deja de mostrarse sola (null: hasta que se despublique)
    @Column(name = "visible_hasta")
    private LocalDate visibleHasta;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_id", nullable = false)
    private Usuario creadoPor;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** Publicar completa la fecha de publicación; despublicar la vuelve a borrador. */
    public void publicar(boolean publicar, LocalDateTime ahora) {
        if (publicar && !Boolean.TRUE.equals(publicada)) {
            publicadaAt = ahora;
        }
        if (!publicar) {
            publicadaAt = null;
        }
        publicada = publicar;
        updatedAt = ahora;
    }

    public EstadoNovedad estado(LocalDate hoy) {
        if (!Boolean.TRUE.equals(publicada)) {
            return EstadoNovedad.BORRADOR;
        }
        return visibleHasta != null && visibleHasta.isBefore(hoy) ? EstadoNovedad.VENCIDA : EstadoNovedad.PUBLICADA;
    }
}
