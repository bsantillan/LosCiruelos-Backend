package com.LosCiruelos.padel_club_api.usuarios.entity;

import com.LosCiruelos.padel_club_api.Exceptions.CategoriaException;
import java.time.LocalDateTime;

import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Categoria;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Posicion;

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

    /** Perfil de jugador completo: datos personales del usuario, categoría y posición. */
    public boolean estaCompleto() {
        return usuario.datosPersonalesCompletos() && categoria != null && posicion != null;
    }

    /**
     * El cliente puede cambiar su categoría una sola vez y dentro de las 24 hs desde que la
     * cargó. La primera carga (por ejemplo, después de entrar con Google) no cuenta como cambio.
     */
    public boolean puedeCambiarCategoria(LocalDateTime ahora) {
        if (categoria == null) {
            return true;
        }
        if (Boolean.TRUE.equals(categoriaModificada)) {
            return false;
        }
        return categoriaActualizadaAt == null || !ahora.isAfter(categoriaActualizadaAt.plusHours(24));
    }

    /**
     * Cambia la categoría si es distinta. La primera carga arranca el plazo de 24 hs; un cambio
     * posterior la marca como modificada (no se puede volver a cambiar).
     */
    public void cambiarCategoria(Categoria nueva, LocalDateTime ahora) {
        if (nueva == categoria) {
            return;
        }
        if (!puedeCambiarCategoria(ahora)) {
            throw new CategoriaException(Boolean.TRUE.equals(categoriaModificada)
                    ? "Ya modificaste tu categoría. Contactá al club para realizar otro cambio."
                    : "El período para modificar tu categoría expiró. Contactá al club para realizar cambios.");
        }
        if (categoria == null) {
            categoriaActualizadaAt = ahora;
        } else {
            categoriaModificada = true;
        }
        categoria = nueva;
    }
}
