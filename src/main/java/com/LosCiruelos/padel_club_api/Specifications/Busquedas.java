package com.LosCiruelos.padel_club_api.Specifications;

import java.text.Normalizer;
import java.util.Locale;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

/**
 * Búsqueda de texto sin distinguir mayúsculas ni tildes: el texto se normaliza en Java y la
 * columna en la base con lower + translate (funciona en PostgreSQL sin extensiones).
 */
public final class Busquedas {

    private static final String CON_TILDE = "áéíóúüàèìòùâêîôûäëïöñ";
    private static final String SIN_TILDE = "aeiouuaeiouaeiouaeion";
    private static final char ESCAPE = '\\';

    private Busquedas() {
    }

    /** "  Pérez " → "perez"; null o vacío → null. */
    public static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String sinTildes = Normalizer.normalize(texto.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** Patrón "contiene" para LIKE, con %, _ y \ del usuario escapados. */
    public static String contiene(String normalizado) {
        return "%" + normalizado.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** La columna en minúsculas y sin tildes, para comparar con un texto normalizado. */
    public static Expression<String> sinTildes(CriteriaBuilder cb, Expression<String> columna) {
        return cb.function("translate", String.class, cb.lower(columna), cb.literal(CON_TILDE),
                cb.literal(SIN_TILDE));
    }

    /**
     * Nombre, apellido, email o "nombre apellido" contienen el texto (ya normalizado).
     * {@code usuario} es la entidad Usuario o un join a ella.
     */
    public static Predicate nombreApellidoOEmail(CriteriaBuilder cb, Path<?> usuario, String normalizado) {
        String patron = contiene(normalizado);
        Expression<String> nombre = usuario.get("nombre");
        Expression<String> apellido = usuario.get("apellido");
        Expression<String> nombreCompleto = cb.concat(cb.concat(cb.coalesce(nombre, ""), " "),
                cb.coalesce(apellido, ""));
        return cb.or(
                cb.like(sinTildes(cb, nombre), patron, ESCAPE),
                cb.like(sinTildes(cb, apellido), patron, ESCAPE),
                cb.like(sinTildes(cb, usuario.get("email")), patron, ESCAPE),
                cb.like(sinTildes(cb, nombreCompleto), patron, ESCAPE));
    }
}
