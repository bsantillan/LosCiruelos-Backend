package com.LosCiruelos.padel_club_api.club.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.LosCiruelos.padel_club_api.club.entity.enums.TipoCancha;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
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

    /** Una cancha tiene hasta estas fotos. */
    public static final int MAXIMO_DE_FOTOS = 6;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // único
    @Column(nullable = false, name = "numero")
    private Integer numero;

    // opcional, ej. "Cancha central"
    @Column(length = 40)
    private String nombre;

    @Column(name = "descripcion", length = 300)
    private String descripcion;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoCancha tipo = TipoCancha.TECHADA;

    // ej. "Cemento", "Césped sintético"
    @Column(length = 60)
    private String superficie;

    @Builder.Default
    @Column(nullable = false)
    private Boolean iluminacion = false;

    // una cancha inactiva no aparece en la disponibilidad ni acepta reservas, partidos ni turnos fijos
    @Builder.Default
    @Column(nullable = false)
    private Boolean activa = true;

    @Builder.Default
    @OrderBy("orden")
    @OneToMany(mappedBy = "cancha", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CanchaFoto> fotos = new ArrayList<>();

    public boolean estaActiva() {
        return Boolean.TRUE.equals(activa);
    }

    /** @throws EstadoInvalidoException (409) si ya tiene {@value #MAXIMO_DE_FOTOS} fotos */
    public void exigirLugarParaOtraFoto() {
        if (fotos.size() >= MAXIMO_DE_FOTOS) {
            throw new EstadoInvalidoException("La cancha ya tiene " + MAXIMO_DE_FOTOS + " fotos: sacá una antes de subir otra.");
        }
    }

    /** @throws EstadoInvalidoException (409) si ya tiene {@value #MAXIMO_DE_FOTOS} fotos */
    public CanchaFoto agregarFoto(String archivo) {
        exigirLugarParaOtraFoto();
        CanchaFoto foto = CanchaFoto.builder().cancha(this).archivo(archivo).orden(fotos.size() + 1).build();
        fotos.add(foto);
        return foto;
    }

    /** Saca la foto y corre las que siguen. @return el archivo, para borrarlo del disco */
    public String sacarFoto(Long fotoId) {
        CanchaFoto foto = fotos.stream().filter(f -> f.getId().equals(fotoId)).findFirst()
                .orElseThrow(() -> new com.LosCiruelos.padel_club_api.club.exception.CanchaNotFoundException(
                        "Foto no encontrada: " + fotoId));
        fotos.remove(foto);
        renumerar();
        return foto.getArchivo();
    }

    /**
     * Ordena las fotos como en {@code ids} (tienen que estar todas, una vez cada una).
     *
     * @throws ReservaException (400) si falta o sobra alguna
     */
    public void ordenarFotos(List<Long> ids) {
        Set<Long> actuales = new HashSet<>(fotos.stream().map(CanchaFoto::getId).toList());
        if (ids == null || ids.size() != actuales.size() || !actuales.equals(new HashSet<>(ids))) {
            throw new ReservaException("Mandá todas las fotos de la cancha, una vez cada una, en el orden nuevo.");
        }
        fotos.sort(java.util.Comparator.comparingInt(f -> ids.indexOf(f.getId())));
        renumerar();
    }

    private void renumerar() {
        for (int i = 0; i < fotos.size(); i++) {
            fotos.get(i).setOrden(i + 1);
        }
    }
}
