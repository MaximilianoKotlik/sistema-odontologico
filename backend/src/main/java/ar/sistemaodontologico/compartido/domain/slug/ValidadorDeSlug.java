package ar.sistemaodontologico.compartido.domain.slug;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.util.regex.Pattern;

/** Reglas del slug del link público (spec slug-consultorio, RN-TE-03). */
public final class ValidadorDeSlug {

    public static final int LARGO_MINIMO = 3;
    public static final int LARGO_MAXIMO = 40;

    // Mismo patrón que ck_consultorio_slug en V1.
    private static final Pattern FORMATO = Pattern.compile("^([a-z0-9])(-?[a-z0-9])+$");

    private ValidadorDeSlug() {
    }

    public static void validar(String slug) {
        if (!tieneFormatoValido(slug)) {
            throw new ValidacionDeNegocio("SLUG_INVALIDO", "El slug no cumple el formato");
        }
        if (PalabrasReservadas.contiene(slug)) {
            throw new ValidacionDeNegocio("SLUG_RESERVADO", "El slug es una palabra reservada");
        }
    }

    static boolean tieneFormatoValido(String slug) {
        return slug != null
                && slug.length() >= LARGO_MINIMO
                && slug.length() <= LARGO_MAXIMO
                && FORMATO.matcher(slug).matches();
    }
}
