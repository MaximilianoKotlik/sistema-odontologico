package ar.sistemaodontologico.agenda.domain;

import ar.sistemaodontologico.compartido.domain.error.ValidacionDeNegocio;
import java.util.regex.Pattern;

/**
 * DNI del paciente, su identidad dentro del consultorio (DD-24): solo dígitos, 7 u 8. Quita los puntos y los espacios
 * con que se suele escribir. No aparece en {@link #toString()} (RN-GL-01).
 */
public record Dni(String valor) {

    public static final String CODIGO_INVALIDO = "DNI_INVALIDO";
    private static final Pattern FORMATO = Pattern.compile("^[0-9]{7,8}$");
    private static final Pattern SEPARADORES = Pattern.compile("[.\\s]");

    public Dni {
        String normalizado = valor == null ? "" : SEPARADORES.matcher(valor).replaceAll("");
        if (!FORMATO.matcher(normalizado).matches()) {
            throw new ValidacionDeNegocio(CODIGO_INVALIDO, "El DNI tiene que tener 7 u 8 dígitos");
        }
        valor = normalizado;
    }

    @Override
    public String toString() {
        return "Dni[***]";
    }
}
