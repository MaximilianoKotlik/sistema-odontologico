package ar.sistemaodontologico.compartido.domain.error;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Error esperado de una regla de negocio, con un {@code codigo} estable que el frontend traduce
 * (spec errores-api, RN-GL-03).
 *
 * <p><strong>El mensaje se le muestra al cliente</strong>: {@code ManejadorGlobalDeErrores} lo devuelve como
 * {@code detail} del Problem Details. Por eso tiene que ser un texto fijo y genérico ("No se encontró turno"),
 * <strong>nunca con datos personales</strong> ni valores ingresados por el usuario (RN-GL-01, Ley 25.326).
 */
public abstract class ErrorDeNegocio extends RuntimeException {

    private static final Pattern FORMATO_CODIGO = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");

    private final String codigo;
    private final TipoDeError tipo;
    private final Map<String, Object> propiedades;

    protected ErrorDeNegocio(String codigo, TipoDeError tipo, String mensaje) {
        this(codigo, tipo, mensaje, Map.of());
    }

    /**
     * Con propiedades extra para el Problem Details, por ejemplo las {@code sugerencias} de {@code SLUG_EN_USO}
     * (C-08, design D6). Como el mensaje, se le muestran al cliente: nunca llevan datos personales.
     */
    protected ErrorDeNegocio(String codigo, TipoDeError tipo, String mensaje, Map<String, Object> propiedades) {
        super(mensaje, null, false, false);
        Objects.requireNonNull(codigo, "codigo");
        if (!FORMATO_CODIGO.matcher(codigo).matches()) {
            throw new IllegalArgumentException("El código de error debe ser MAYUSCULAS_CON_GUIONES_BAJOS");
        }
        this.codigo = codigo;
        this.tipo = Objects.requireNonNull(tipo, "tipo");
        this.propiedades = Map.copyOf(Objects.requireNonNull(propiedades, "propiedades"));
    }

    public String getCodigo() {
        return codigo;
    }

    public TipoDeError getTipo() {
        return tipo;
    }

    /** Propiedades extra del Problem Details; vacío si no hay. */
    public Map<String, Object> getPropiedades() {
        return propiedades;
    }
}
