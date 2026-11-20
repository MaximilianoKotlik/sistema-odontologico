package ar.sistemaodontologico.compartido.domain.slug;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Sugiere un slug válido a partir del nombre del consultorio (spec slug-consultorio). La unicidad
 * (sufijos -2, -3…) la resuelve quien tiene acceso al repositorio (C-04).
 */
public final class SugeridorDeSlug {

    static final String SUFIJO = "-consultorio";
    static final String POR_DEFECTO = "consultorio";

    private SugeridorDeSlug() {
    }

    public static String sugerir(String nombre) {
        String base = recortar(normalizar(nombre == null ? "" : nombre), ValidadorDeSlug.LARGO_MAXIMO);
        if (base.isEmpty()) {
            return POR_DEFECTO;
        }
        if (base.length() < ValidadorDeSlug.LARGO_MINIMO || PalabrasReservadas.contiene(base)) {
            return recortar(base, ValidadorDeSlug.LARGO_MAXIMO - SUFIJO.length()) + SUFIJO;
        }
        return base;
    }

    private static String normalizar(String nombre) {
        String sinMarcas = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return sinMarcas.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static String recortar(String slug, int largoMaximo) {
        String recortado = slug.length() > largoMaximo ? slug.substring(0, largoMaximo) : slug;
        return recortado.replaceAll("-+$", "");
    }
}
