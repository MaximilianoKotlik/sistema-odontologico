package ar.sistemaodontologico.agenda.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Qué busca el odontólogo al elegir un paciente (C-12, design D7; spec busqueda-de-pacientes). Un texto de solo dígitos,
 * sin contar puntos, espacios, {@code +} ni guiones, busca por DNI o teléfono; uno con letras, por las palabras del
 * nombre o el apellido. El texto recortado tiene al menos 3 caracteres, y uno de dígitos, al menos 3 dígitos.
 */
public sealed interface CriterioDeBusqueda {

    int LARGO_MINIMO = 3;

    /** El DNI que empieza con estos dígitos o el teléfono que los contiene. */
    record PorDigitos(String digitos) implements CriterioDeBusqueda {

        public PorDigitos {
            Objects.requireNonNull(digitos, "digitos");
        }

        @Override
        public String toString() {
            return "PorDigitos[***]";
        }
    }

    /** El nombre o el apellido que empiezan con alguna de estas palabras, en minúsculas. */
    record PorPalabras(List<String> palabras) implements CriterioDeBusqueda {

        public PorPalabras {
            palabras = List.copyOf(palabras);
        }

        @Override
        public String toString() {
            return "PorPalabras[***]";
        }
    }

    Pattern SEPARADORES_NUMERICOS = Pattern.compile("[.\\s+\\-]");
    Pattern SOLO_DIGITOS = Pattern.compile("\\d+");

    static CriterioDeBusqueda de(String texto) {
        String recortado = texto == null ? "" : texto.strip();
        if (recortado.length() < LARGO_MINIMO) {
            throw Textos.invalido();
        }
        String sinSeparadores = SEPARADORES_NUMERICOS.matcher(recortado).replaceAll("");
        if (SOLO_DIGITOS.matcher(sinSeparadores).matches()) {
            if (sinSeparadores.length() < LARGO_MINIMO) {
                throw Textos.invalido();
            }
            return new PorDigitos(sinSeparadores);
        }
        return new PorPalabras(Arrays.stream(recortado.toLowerCase(java.util.Locale.ROOT).split("\\s+"))
                .filter(palabra -> !palabra.isEmpty())
                .distinct()
                .toList());
    }
}
