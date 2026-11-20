package ar.sistemaodontologico.compartido.domain.error;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Error esperado de una regla de negocio, con un {@code codigo} estable que el frontend traduce
 * (spec errores-api, RN-GL-03). El mensaje es para el log: nunca incluye datos personales.
 */
public abstract class ErrorDeNegocio extends RuntimeException {

    private static final Pattern FORMATO_CODIGO = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");

    private final String codigo;
    private final TipoDeError tipo;

    protected ErrorDeNegocio(String codigo, TipoDeError tipo, String mensaje) {
        super(mensaje, null, false, false);
        Objects.requireNonNull(codigo, "codigo");
        if (!FORMATO_CODIGO.matcher(codigo).matches()) {
            throw new IllegalArgumentException("El código de error debe ser MAYUSCULAS_CON_GUIONES_BAJOS");
        }
        this.codigo = codigo;
        this.tipo = Objects.requireNonNull(tipo, "tipo");
    }

    public String getCodigo() {
        return codigo;
    }

    public TipoDeError getTipo() {
        return tipo;
    }
}
