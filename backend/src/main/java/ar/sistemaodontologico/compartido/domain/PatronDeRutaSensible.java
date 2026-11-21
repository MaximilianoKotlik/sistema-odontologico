package ar.sistemaodontologico.compartido.domain;

import java.util.regex.Pattern;

/**
 * Rutas que llevan una credencial: el segmento después de {@code /public/turnos/} es el token de gestión del paciente
 * (C-15, design D10). Se enmascara en logs y en el {@code instance} de los Problem Details.
 */
public final class PatronDeRutaSensible {

    private static final Pattern TOKEN_EN_RUTA = Pattern.compile("(/public/turnos/)[^/?#\\s\"']+");

    private PatronDeRutaSensible() {
    }

    public static String enmascarar(String texto) {
        return texto == null ? null : TOKEN_EN_RUTA.matcher(texto).replaceAll("$1***");
    }
}
