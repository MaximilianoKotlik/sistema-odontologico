package ar.sistemaodontologico.compartido.domain.error;

import java.util.Map;

/** La operación choca con el estado actual (HTTP 409). */
public class ConflictoDeNegocio extends ErrorDeNegocio {

    public ConflictoDeNegocio(String codigo, String mensaje) {
        super(codigo, TipoDeError.CONFLICTO, mensaje);
    }

    public ConflictoDeNegocio(String codigo, String mensaje, Map<String, Object> propiedades) {
        super(codigo, TipoDeError.CONFLICTO, mensaje, propiedades);
    }
}
