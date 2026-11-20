package ar.sistemaodontologico.compartido.domain.slug;

import java.util.Locale;
import java.util.Set;

/**
 * Slugs que no puede usar un consultorio porque chocan con rutas del producto (RN-TE-03, design D6).
 * Vive en la aplicación y no en la base para poder ampliarla sin migración.
 */
public final class PalabrasReservadas {

    private static final Set<String> RESERVADAS = Set.of(
            "app", "api", "login", "registro", "precios", "admin", "c", "t",
            "www", "static", "assets", "health", "docs", "swagger", "v3");

    private PalabrasReservadas() {
    }

    public static boolean contiene(String palabra) {
        return palabra != null && RESERVADAS.contains(palabra.toLowerCase(Locale.ROOT));
    }
}
